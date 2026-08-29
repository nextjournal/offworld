(ns nextjournal.offworld-guard-test
  (:require
   #?(:clj  [clojure.test :refer [deftest is use-fixtures]]
      :cljs [cljs.test :refer-macros [deftest is use-fixtures]])
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.guard :as 🛡]))

(def ^:dynamic *ran* nil)

(defn- note! [k] (swap! *ran* conj k))

(defn- fixture [f]
  (let [old      @nxr/!registry
        shipped  (get-in old [:nexus/expansions ::🛡/when])]
    (reset! nxr/!registry {})
    (nxr/register-system->state! deref)
    (swap! nxr/!registry assoc-in [:nexus/expansions ::🛡/when] shipped)
    (binding [*ran* (atom [])]
      (try (f) (finally (reset! nxr/!registry old))))))

(use-fixtures :each fixture)

(defn- run [actions]
  (nexus/dispatch (🪐/client-nexus (nxr/get-registry)) (atom {}) {:enter? true :other? false} actions))

(deftest the-shipped-guard-is-registered-and-client-side
  (let [h (get-in (nxr/get-registry) [:nexus/expansions ::🛡/when])]
    (is (some? h) "nextjournal.offworld.guard registers it on load")
    (is (🪐/client-marked? h) "and it expands in the browser, which is the point")))

(deftest expands-to-its-actions-when-the-predicate-is-truthy
  (nxr/register-effect! ::client-fx (fn [& _] (note! :ran)))
  (run [[::🛡/when true [::client-fx]]])
  (is (= [:ran] @*ran*)))

(deftest expands-to-nothing-when-the-predicate-is-falsey
  (nxr/register-effect! ::client-fx (fn [& _] (note! :ran)))
  (run [[::🛡/when false [::client-fx]]])
  (is (= [] @*ran*)))

(deftest guards-several-actions-at-once
  (nxr/register-effect! ::a (fn [& _] (note! :a)))
  (nxr/register-effect! ::b (fn [& _] (note! :b)))
  (run [[::🛡/when true [::a] [::b]]])
  (is (= [:a :b] @*ran*)))

(deftest resolves-its-predicate-from-a-client-placeholder
  (nxr/register-placeholder! ::enter? (fn [dd] (:enter? dd)))
  (nxr/register-placeholder! ::other? (fn [dd] (:other? dd)))
  (nxr/register-effect! ::client-fx (fn [& _] (note! :ran)))
  (run [[::🛡/when [::other?] [::client-fx]]])
  (is (= [] @*ran*) "a client-dispatch-time value decides, and this one says no")
  (run [[::🛡/when [::enter?] [::client-fx]]])
  (is (= [:ran] @*ran*) "and this one says yes"))

(deftest elides-the-round-trip
  (nxr/register-placeholder! ::enter? (fn [dd] (:enter? dd)))
  (nxr/register-placeholder! ::other? (fn [dd] (:other? dd)))
  (nxr/register-effect! ::commit ^::🪐/server (fn [& _] (note! :server-ran)))
  (let [ctx (run [[::🛡/when [::other?] [::commit "value"]]])]
    (is (nil? (::🪐/server-actions ctx))
        "the guard closed, so nothing is bound for the server"))
  (let [ctx (run [[::🛡/when [::enter?] [::commit "value"]]])]
    (is (= [[::commit "value"]] (::🪐/server-actions ctx))
        "the guard opened, and the server action diverts as usual")
    (is (= [] @*ran*) "still not executed on the client")))
