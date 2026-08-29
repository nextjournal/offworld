(ns nextjournal.offworld-divert-test
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing use-fixtures]]
      :cljs [cljs.test :refer-macros [deftest is testing use-fixtures]])
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]))

(def ^:dynamic *ran* nil)

(defn- note! [k] (swap! *ran* conj k))

(defn- fixture [f]
  (let [old @nxr/!registry]
    (reset! nxr/!registry {})
    (nxr/register-system->state! deref)
    (binding [*ran* (atom [])]
      (try (f) (finally (reset! nxr/!registry old))))))

(use-fixtures :each fixture)

(defn- run [actions]
  (nexus/dispatch (🪐/client-nexus (nxr/get-registry)) (atom {}) {:probe 42} actions))

(deftest diverts-a-server-effect
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _] (note! :server-ran)))
  (let [ctx (run [[::server-fx 1]])]
    (is (= [[::server-fx 1]] (::🪐/server-actions ctx)) "stashed for the server")
    (is (= [] @*ran*) "and not executed on the client")))

(deftest diverts-from-inside-a-client-expansion
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _] (note! :server-ran)))
  (nxr/register-effect! ::client-fx (fn [& _] (note! :client-ran)))
  (nxr/register-expansion! ::client-xp (fn [_] [[::server-fx] [::client-fx]]))
  (let [ctx (run [[::client-xp]])]
    (is (= [[::server-fx]] (::🪐/server-actions ctx)) "diverted at depth")
    (is (= [:client-ran] @*ran*) "client half still runs, server half does not")))

(deftest interpolates-client-placeholders-before-diverting
  (nxr/register-placeholder! ::probe (fn [dd] (:probe dd)))
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _]))
  (let [ctx (run [[::server-fx [::probe]]])]
    (is (= [[::server-fx 42]] (::🪐/server-actions ctx))
        "a client-stage value resolves before it crosses")))

(deftest leaves-server-placeholders-unresolved-on-the-client
  (nxr/register-placeholder! ::server-probe ^::🪐/server (fn [_] :RESOLVED-ON-CLIENT))
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _]))
  (let [ctx (run [[::server-fx [::server-probe]]])]
    (is (= [[::server-fx [::server-probe]]] (::🪐/server-actions ctx))
        "a server-stage placeholder is carried, not consumed, on the client")))

(deftest csr-runs-everything-locally
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _] (note! :server-ran)))
  (let [ctx (nexus/dispatch (nxr/get-registry) (atom {}) {} [[::server-fx]])]
    (is (nil? (::🪐/server-actions ctx)) "nothing is diverted without the client view")
    (is (= [:server-ran] @*ran*))))
