(ns nextjournal.offworld-claim-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.claim :as claim]
   [nextjournal.offworld.conn :as conn]
   [nextjournal.offworld.standard :as std]))

(def ^:dynamic seen nil)

(defn- fixture [f]
  (let [old @nxr/!registry]
    (reset! nxr/!registry {})
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (reset! conn/!store {})
    (nxr/register-effect! ::save ^::🪐/server (fn [_ _ v] (reset! seen v)))
    (binding [seen (atom nil)]
      (try (f) (finally (reset! nxr/!registry old))))))

(use-fixtures :each fixture)

(defn- server-run [conn-id actions]
  (nexus/dispatch (nxr/get-registry) (atom {}) {::🪐/conn-id conn-id} actions)
  @seen)

(deftest the-intent-carries-a-token-not-the-value
  (binding [conn/*conn-id* "conn-1"]
    (let [big {:rows (range 1000)}
          ref (claim/stash! big)]
      (is (= ::claim/value (first ref)) "a reference, resolved by the server")
      (is (string? (second ref)) "and what travels is an opaque token")
      (is (not= big (second ref))))))

(deftest resolves-back-to-the-value-on-the-server
  (binding [conn/*conn-id* "conn-1"]
    (let [big {:rows (range 3)}]
      (is (= big (server-run "conn-1" [[::save (claim/stash! big)]]))))))

(deftest a-token-is-useless-in-another-connection
  (binding [conn/*conn-id* "conn-1"]
    (let [ref (claim/stash! {:secret true})]
      (is (nil? (server-run "conn-2" [[::save ref]]))
          "redeeming it elsewhere yields nothing"))))

(deftest the-client-carries-it-untouched
  (binding [conn/*conn-id* "conn-1"]
    (let [ref (claim/stash! {:rows (range 3)})
          ctx (nexus/dispatch (🪐/client-nexus (nxr/get-registry))
                              (atom {}) {} [[::save ref]])]
      (is (= [[::save ref]] (::🪐/server-actions ctx))
          "a server-world placeholder crosses as the reference it is"))))
