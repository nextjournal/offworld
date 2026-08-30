(ns nextjournal.offworld-inline-test
  (:require
   [clojure.test :refer [deftest is]]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.inline :as inline]
   [nextjournal.offworld.standard :as std]))

(deftest inline-defers-a-closure-behind-a-token
  (let [ran (atom false)]
    (binding [inline/*conn-id* "conn-1"]
      (let [actions      (inline/inline (reset! ran true))
            [[k tok]]    actions]
        (is (= ::inline/invoke k) "emits a server effect, not the closure")
        (is (string? tok) "the wire carries a token")
        (is (false? @ran) "nothing runs at render time")
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-1"}) {} actions)
        (is (true? @ran) "and runs when the server resolves the token")))))

(deftest inline-is-scoped-to-its-connection
  (let [ran (atom false)]
    (binding [inline/*conn-id* "conn-1"]
      (let [actions (inline/inline (reset! ran true))]
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-2"}) {} actions)
        (is (false? @ran) "another connection cannot invoke it")
        (inline/release! "conn-1")
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-1"}) {} actions)
        (is (false? @ran) "and it is gone once the connection closes")))))
