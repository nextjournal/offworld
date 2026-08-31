(ns nextjournal.offworld.staging-test
  "Tests for the staging analysis. Portable: runs under clojure.test (bb
  test-clj) and cljs.test / :node-test (bb test-cljs)."
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   [nexus.core :as nexus]
   [nextjournal.offworld.staging :as staging]
   [nextjournal.offworld :as-alias ow]))

(def ^:private nexus
  "A registry with one action / effect / placeholder per world. Server handlers
  carry the ^::🪐/server marker; unmarked handlers default to client."
  {:nexus/expansions   {:ex/client (fn [])
                        :ex/server ^::ow/server (fn [])}
   :nexus/effects      {:fx/client (fn [])
                        :fx/server ^::ow/server (fn [])}
   :nexus/placeholders {:pl/client (fn [])
                        :pl/server ^::ow/server (fn [])}})

(deftest lookup-worlds
  (testing "a handler's world is its stage — every kind, both worlds. There is no
            finer rung: nexus drains a dispatch to completion, so nothing is
            stranded by being late *within* a world"
    (is (= :client (:world (staging/lookup nexus :pl/client))))
    (is (= :server (:world (staging/lookup nexus :pl/server))))
    (is (= :client (:world (staging/lookup nexus :fx/client))))
    (is (= :server (:world (staging/lookup nexus :fx/server))))
    (is (= :client (:world (staging/lookup nexus :ex/client))))
    (is (= :server (:world (staging/lookup nexus :ex/server)))))
  (testing "the ladder puts request between the two worlds — the boundary a
            client reference must resolve before"
    (is (= [:render :morph :client :request :server] staging/stage-order))
    (let [n (into {} (map-indexed (fn [i st] [st i]) staging/stage-order))]
      (is (< (n :client) (n :request) (n :server)))))
  (testing "unregistered key"
    (is (= :unknown (:kind (staging/lookup nexus :no/such))))
    (is (= :unknown (:world (staging/lookup nexus :no/such))))))

(deftest tagging
  (let [tagged (staging/tag nexus [[:fx/server [:pl/client "x"]]])
        action (first tagged)
        ph     (first (filter staging/keyword-headed? (rest action)))]
    (is (= :effect (:kind (staging/info action))) "action head tagged")
    (is (= :placeholder (:kind (staging/info ph))) "nested placeholder tagged")
    (is (= :client (:world (staging/info ph))))))

(deftest stranded-at-server
  (testing "a client placeholder that leaked into the server payload is flagged"
    (let [vs (staging/stranded-at-server nexus [[:fx/server [:pl/client "x"]]])]
      (is (= 1 (count vs)))
      (is (= :stranded-client-ref (:type (first vs))))
      (is (= :pl/client (:key (first vs))))))
  (testing "a server placeholder in the server payload is fine — it resolves server-side"
    (is (empty? (staging/stranded-at-server nexus [[:fx/server [:pl/server "x"]]]))))
  (testing "an already-resolved value (not a ref) is ignored"
    (is (empty? (staging/stranded-at-server nexus [[:fx/server "literal"]]))))
  (testing "all three client kinds strand: action, effect, placeholder"
    (let [vs (staging/stranded-at-server
              nexus [[:fx/server [:ex/client] [:fx/client] [:pl/client "x"]]])]
      (is (= #{:ex/client :fx/client :pl/client} (set (map :key vs))))))
  (testing "empty payload is clean"
    (is (empty? (staging/stranded-at-server nexus [])))))

(deftest unregistered-actions
  (testing "a typo'd dispatch head is flagged"
    (is (= [:effcts/save]
           (mapv :key (staging/unregistered-actions nexus [[:effcts/save "typo"]])))))
  (testing "registered heads are clean (even server ones)"
    (is (empty? (staging/unregistered-actions nexus [[:ex/server] [:fx/server 1]])))))

;; ---------------------------------------------------------------------------
;; Precedence: one key registered under several kinds. nexus resolves the same
;; head positionally — placeholder (interpolation runs first) > expansion
;; (expand-actions checks :nexus/expansions, then the legacy :nexus/actions) >
;; effect (the drain). `lookup` must classify by that same order.

(def ^:private collision-nexus
  {:nexus/placeholders {:ph+ex (fn [])}                    ; also an expansion
   :nexus/expansions   {:ph+ex (fn [])                     ; also a placeholder
                        :ex+act (fn [])                    ; also a legacy action
                        :ex+fx (fn [])}                    ; also an effect
   :nexus/actions      {:ex+act (fn [])}
   :nexus/effects      {:ex+fx (fn [])}})

(deftest collision-precedence
  (testing "placeholder > expansion > action > effect — matches nexus runtime"
    (is (= :placeholder (:kind (staging/lookup collision-nexus :ph+ex)))
        "interpolation runs first, so a placeholder shadows a same-key expansion")
    (is (= :expansion (:kind (staging/lookup collision-nexus :ex+act)))
        "expand-actions checks :nexus/expansions before the legacy :nexus/actions")
    (is (= :expansion (:kind (staging/lookup collision-nexus :ex+fx)))
        "an expansion handler wins over the effect drain for the same head")))

(deftest expansion-stranded-at-server
  (testing "a client expansion surviving into server-bound actions is flagged —
            an expansion strands exactly like a placeholder or an effect does,
            since the world is the whole of what decides"
    (let [v (first (staging/stranded-at-server
                    {:nexus/expansions {:ex/client (fn [])}} [[:ex/client "x"]]))]
      (is (= :stranded-client-ref (:type v)))
      (is (= :ex/client (:key v)))
      (is (= :expansion (:kind v)))
      (is (= :client (:world v)))
      (is (string? (:message v))))))

;; ---------------------------------------------------------------------------
;; The runtime checker. Static analysis sees what the source spells; this sees
;; what the handlers actually produced.

(defn- caught
  "Dispatch `actions` against `nexus-map` with the checker installed, and return
  the violations it reported."
  [nexus-map actions & [{:keys [world] :or {world :server}}]]
  (let [seen (atom [])
        nx   (-> nexus-map
                 (assoc :nexus/system->state deref)
                 (update :nexus/interceptors (fnil conj [])
                         (staging/checker {:world world
                                           :on-violation #(swap! seen into %)})))]
    (staging/warn-on!)
    (try (nexus/dispatch nx (atom {}) {} actions)
         (finally (staging/warn-off!)))
    @seen))

(deftest checker-sees-what-an-expansion-produced
  (let [nx {:nexus/expansions {:ex/emit (fn [_] [[:fx/typo]])}
            :nexus/effects    {:fx/known (fn [& _])}}]
    (is (= [:fx/typo] (map :key (caught nx [[:ex/emit]] {:world :client})))
        "an unregistered action that only exists after expansion")
    (is (empty? (staging/unregistered-actions nx [[:ex/emit]]))
        "which checking the dispatch you authored cannot see")))

(deftest checker-flags-a-client-ref-that-reached-a-server-stage
  (let [nx {:nexus/effects      {:fx/server ^::ow/server (fn [& _])}
            :nexus/placeholders {:pl/client (fn [_] :x)}}
        vs (caught nx [[:fx/server [:pl/client]]] {:world :server})]
    (is (some #(= :stranded-client-ref (:type %)) vs))
    (is (empty? (filter #(= :stranded-client-ref (:type %))
                        (caught nx [[:fx/server [:pl/client]]] {:world :client})))
        "and the same reference is perfectly legal while still on the client")))

(deftest checker-is-silent-unless-warning-is-on
  (let [seen (atom [])
        nx   {:nexus/system->state deref
              :nexus/effects       {}
              :nexus/interceptors  [(staging/checker
                                    {:world :server
                                     :on-violation #(swap! seen into %)})]}]
    (staging/warn-off!)
    (nexus/dispatch nx (atom {}) {} [[:fx/nope]])
    (is (= [] @seen))))
