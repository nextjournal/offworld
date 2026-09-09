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

(defonce !shared (atom 0))

(deftest identical-bodies-share-one-address
  (let [[[_ t1]] (inline/inline (swap! !shared inc))
        [[_ t2]] (inline/inline (swap! !shared inc))]
    (is (inline/derived-token? t1) "a body reading no local is named by its form")
    (is (= t1 t2) "so two sites spelling it the same way are one entry")))

(deftest a-machine-named-symbol-does-not-move-the-address
  (let [[[_ t1]] (inline/inline (swap! !shared (fn [n] (inc n)) #(identity %)))
        [[_ t2]] (inline/inline (swap! !shared (fn [n] (inc n)) #(identity %)))]
    (is (= t1 t2) "the reader numbers each #() afresh; the address ignores that")))

(defonce !outlives (atom 0))

(deftest a-derived-body-outlives-every-connection
  (reset! !outlives 0)
  (let [actions (inline/inline (swap! !outlives inc))]
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (inline/release! "conn-9")
    (nexus/dispatch (nxr/get-registry)
                    (atom {::🪐/conn-id "conn-9"}) {} actions)
    (is (= 1 @!outlives) "no connection was holding it, and none had to be")))

(deftest a-captured-local-keeps-the-bodies-apart
  (let [seen (atom [])]
    (binding [inline/*conn-id* "conn-1"]
      (let [dispatches (doall (for [v [:a :b]] (inline/inline (swap! seen conj v))))
            tokens     (map (comp second first) dispatches)]
        (is (not-any? inline/derived-token? tokens)
            "the form is not the whole of a body that reads its scope")
        (is (apply not= tokens) "so each instance is named separately")
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (doseq [actions dispatches]
          (nexus/dispatch (nxr/get-registry)
                          (atom {::🪐/conn-id "conn-1"}) {} actions))
        (is (= [:a :b] @seen) "and each runs with the value it closed over")))))

(defonce !per-conn (atom {}))

(deftest a-body-reaches-its-connection-without-capturing-it
  (reset! !per-conn {})
  (let [actions (inline/inline (swap! !per-conn update (inline/conn-id) (fnil inc 0)))
        [[_ tok]] actions]
    (is (inline/derived-token? tok)
        "ambient context is read, not closed over, so the form is the whole of it")
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (doseq [c ["conn-a" "conn-b" "conn-a"]]
      (nexus/dispatch (nxr/get-registry) (atom {::🪐/conn-id c}) {} actions))
    (is (= {"conn-a" 2 "conn-b" 1} @!per-conn)
        "one shared entry, and each dispatch runs for its own connection")))
