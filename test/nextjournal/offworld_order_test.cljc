(ns nextjournal.offworld-order-test
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing use-fixtures]]
      :cljs [cljs.test :refer-macros [deftest is testing use-fixtures]])
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.order :as 📈]))

(def ^:dynamic *ran* nil)

(defn- fixture [f]
  (let [old @nxr/!registry]
    (reset! nxr/!registry {})
    (nxr/register-system->state! deref)
    (nxr/register-effect! ::note (fn [_ _ n] (swap! *ran* conj n)))
    (binding [*ran* (atom [])]
      (try (f) (finally (reset! nxr/!registry old))))))

(use-fixtures :each fixture)

(defn- gated [!state & {:as opts}]
  (update (nxr/get-registry) :nexus/interceptors
          (fnil conj []) (📈/checking !state (or opts {}))))

(defn- seq-gate [n]
  (with-meta [[::note n]] {::🪐/order [:seq-gate :key ::k :seq-num n]}))

(deftest opt-in-nothing-happens-without-the-interceptor
  (nexus/dispatch (nxr/get-registry) (atom {}) {} (seq-gate 7))
  (is (= [7] @*ran*) "an out-of-order dispatch runs anyway when nobody is checking"))

(deftest seq-gate-drops-out-of-order
  (let [!s (atom {})]
    (nexus/dispatch (gated !s) (atom {}) {} (seq-gate 0))
    (nexus/dispatch (gated !s) (atom {}) {} (seq-gate 2))
    (is (= [0] @*ran*) "a dispatch that skips ahead is dropped, not queued")
    (nexus/dispatch (gated !s) (atom {}) {} (seq-gate 1))
    (is (= [0 1] @*ran*) "and the one it skipped still runs when it arrives")))

(deftest unpoliced-dispatches-pass-through
  (let [!s (atom {})]
    (nexus/dispatch (gated !s) (atom {}) {} [[::note :plain]])
    (is (= [:plain] @*ran*))))

(deftest bounded-buffer-holds-a-gap-then-flushes
  (let [!s       (atom {})
        timeouts (atom [])
        policy   #(with-meta [[::note %]]
                    {::🪐/order [:bounded-buffer {:key ::b :seq-num %}]})]
    (nexus/dispatch (gated !s :on-timeout #(swap! timeouts conj %)) (atom {}) {} (policy 1))
    (is (= [] @*ran*) "a gap is buffered, not run")
    (is (= 1 (count @timeouts)) "and a timeout is requested of the host")
    (nexus/dispatch (gated !s) (atom {}) {} (policy 0))
    (is (= [0 1] @*ran*) "contiguity flushes the buffer in order")))

(deftest proposing-stamps-outgoing-server-actions
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _]))
  (let [!s (atom {})
        nx (update (🪐/client-nexus (nxr/get-registry))
                   :nexus/interceptors conj (📈/proposing !s))
        go #(-> (nexus/dispatch nx (atom {}) {} (with-meta [[::server-fx]]
                                                  {::🪐/order [:seq-gate :key ::k]}))
                ::🪐/server-actions meta ::🪐/order)]
    (is (= [:seq-gate :key ::k :seq-num 0] (go)))
    (is (= [:seq-gate :key ::k :seq-num 1] (go)) "counter advances per dispatch")))
