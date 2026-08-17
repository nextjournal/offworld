(ns nextjournal.offworld.staging-test
  "Tests for the staging analysis. Portable: runs under clojure.test (bb
  test-clj) and cljs.test / :node-test (bb test-cljs)."
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   [nextjournal.offworld.staging :as staging]
   [nextjournal.offworld :as-alias ow]))

(def ^:private nexus
  "A registry with one action / effect / placeholder per side. Server handlers
  carry the ^::🪐/server marker; unmarked handlers default to client."
  {:nexus/expansions   {:ex/client (fn [])
                        :ex/server ^::ow/server (fn [])}
   :nexus/effects      {:fx/client (fn [])
                        :fx/server ^::ow/server (fn [])}
   :nexus/placeholders {:pl/client (fn [])
                        :pl/server ^::ow/server (fn [])}})

(deftest lookup-deadlines
  (testing "placeholders strand at fx, not expand — interpolation runs again before fx"
    (is (= :client/fx (:stage (staging/lookup nexus :pl/client))))
    (is (= :server/fx (:stage (staging/lookup nexus :pl/server)))))
  (testing "effects strand at fx; actions at expand"
    (is (= :client/fx     (:stage (staging/lookup nexus :fx/client))))
    (is (= :server/fx     (:stage (staging/lookup nexus :fx/server))))
    (is (= :client/expand (:stage (staging/lookup nexus :ex/client))))
    (is (= :server/expand (:stage (staging/lookup nexus :ex/server)))))
  (testing "sides, and n increases client -> server"
    (is (= :client (:side (staging/lookup nexus :pl/client))))
    (is (= :server (:side (staging/lookup nexus :pl/server))))
    (is (< (:n (staging/lookup nexus :pl/client))
           (:n (staging/lookup nexus :pl/server)))))
  (testing "unregistered key"
    (is (= :unknown (:kind (staging/lookup nexus :no/such))))
    (is (nil? (:stage (staging/lookup nexus :no/such))))))

(deftest tagging
  (let [tagged (staging/tag nexus [[:fx/server [:pl/client "x"]]])
        action (first tagged)
        ph     (first (filter staging/keyword-headed? (rest action)))]
    (is (= :effect (:kind (staging/info action))) "action head tagged")
    (is (= :placeholder (:kind (staging/info ph))) "nested placeholder tagged")
    (is (= :client (:side (staging/info ph))))))

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
            regression: it used to resolve to :stage nil and NPE via (name nil)"
    (let [v (first (staging/stranded-at-server
                    {:nexus/expansions {:ex/client (fn [])}} [[:ex/client "x"]]))]
      (is (= :stranded-client-ref (:type v)))
      (is (= :ex/client (:key v)))
      (is (= :expansion (:kind v)))
      (is (= :client/expand (:stage v)))
      (is (string? (:message v)) "message builds without NPE"))))
