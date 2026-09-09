(ns nextjournal.offworld-inline-test
  (:require
   [clojure.test :refer [deftest is]]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.inline :as inline]
   [nextjournal.offworld.standard :as std]))

(deftest a-body-is-deferred-behind-a-token
  (let [ran (atom false)]
    (binding [inline/*conn-id* "conn-1"]
      (let [action    (inline/server! (reset! ran true))
            [k tok]   action]
        (is (= ::inline/invoke k) "emits a server effect, not the closure")
        (is (string? tok) "the wire carries a token")
        (is (false? @ran) "nothing runs at render time")
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-1"}) {} [action])
        (is (true? @ran) "and runs when the server resolves the token")))))

(deftest a-minted-body-is-scoped-to-its-connection
  (let [ran (atom false)]
    (binding [inline/*conn-id* "conn-1"]
      (let [action (inline/server! (reset! ran true))]
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-2"}) {} [action])
        (is (false? @ran) "another connection cannot invoke it")
        (inline/release! "conn-1")
        (nexus/dispatch (nxr/get-registry)
                        (atom {::🪐/conn-id "conn-1"}) {} [action])
        (is (false? @ran) "and it is gone once the connection closes")))))

(defonce !shared (atom 0))

(deftest identical-bodies-share-one-address
  (let [[_ t1] (inline/server! (swap! !shared inc))
        [_ t2] (inline/server! (swap! !shared inc))]
    (is (inline/derived-token? t1) "a body reading no local is named by its form")
    (is (= t1 t2) "so two sites spelling it the same way are one entry")))

(deftest a-machine-named-symbol-does-not-move-the-address
  (let [[_ t1] (inline/server! (swap! !shared (fn [n] (inc n)) #(identity %)))
        [_ t2] (inline/server! (swap! !shared (fn [n] (inc n)) #(identity %)))]
    (is (= t1 t2) "the reader numbers each #() afresh; the address ignores that")))

(defonce !outlives (atom 0))

(deftest a-derived-body-outlives-every-connection
  (reset! !outlives 0)
  (let [action (inline/server! (swap! !outlives inc))]
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (inline/release! "conn-9")
    (nexus/dispatch (nxr/get-registry)
                    (atom {::🪐/conn-id "conn-9"}) {} [action])
    (is (= 1 @!outlives) "no connection was holding it, and none had to be")))

(deftest a-captured-local-keeps-the-bodies-apart
  (let [seen (atom [])]
    (binding [inline/*conn-id* "conn-1"]
      (let [actions (doall (for [v [:a :b]] (inline/server! (swap! seen conj v))))
            tokens  (map second actions)]
        (is (not-any? inline/derived-token? tokens)
            "the form is not the whole of a body that reads its scope")
        (is (apply not= tokens) "so each instance is named separately")
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (doseq [action actions]
          (nexus/dispatch (nxr/get-registry)
                          (atom {::🪐/conn-id "conn-1"}) {} [action]))
        (is (= [:a :b] @seen) "and each runs with the value it closed over")))))

(defonce !per-conn (atom {}))

(deftest a-body-reaches-its-connection-without-capturing-it
  (reset! !per-conn {})
  (let [action  (inline/server! (swap! !per-conn update (inline/conn-id) (fnil inc 0)))
        [_ tok] action]
    (is (inline/derived-token? tok)
        "ambient context is read, not closed over, so the form is the whole of it")
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (doseq [c ["conn-a" "conn-b" "conn-a"]]
      (nexus/dispatch (nxr/get-registry) (atom {::🪐/conn-id c}) {} [action]))
    (is (= {"conn-a" 2 "conn-b" 1} @!per-conn)
        "one shared entry, and each dispatch runs for its own connection")))

(defonce !slotted (atom nil))

(deftest a-client-value-rides-beside-the-token
  (let [action      (inline/server! (reset! !slotted (inline/client! [:event.target/value])))
        [k tok ref] action]
    (is (= ::inline/invoke k))
    (is (inline/derived-token? tok) "the ref left the body, so the body is its own whole")
    (is (= [:event.target/value] ref)
        "and the client value the intent reads is visible without running it")
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (nexus/dispatch (nxr/get-registry) (atom {}) {}
                    [[::inline/invoke tok "typed"]])
    (is (= "typed" @!slotted) "the slot arrives as an argument")))

(deftest a-hoisted-ref-may-mention-a-local
  (let [path        [:a :b]
        action      (inline/server! (reset! !slotted (inline/client! [::state path])))
        [_ tok ref] action]
    (is (inline/derived-token? tok)
        "the ref is evaluated at render time, outside the body, so nothing is captured")
    (is (= [::state [:a :b]] ref) "and it carries the value the render computed")))

(deftest two-sites-reading-different-values-share-one-address
  (let [[_ t1 r1] (inline/server! (reset! !slotted (inline/client! [:event.target/value])))
        [_ t2 r2] (inline/server! (reset! !slotted (inline/client! [:node/row])))]
    (is (= t1 t2) "the difference is in the slots, not the body")
    (is (not= r1 r2) "which is where it is legible")))

(deftest an-authored-dispatch-is-left-exactly-as-written
  (let [one [[:fx/inc]]
        two [[:fx/inc] [:fx/save [:a] 1]]]
    (is (= one (🪐/->dispatch one)) "nothing is inferred from its shape")
    (is (= two (🪐/->dispatch two)))))

(deftest a-marked-action-is-wrapped-into-one
  (let [action (inline/server! (swap! !shared inc))]
    (is (= ::inline/invoke (first action)) "the macro yields one action")
    (is (get (meta action) 🪐/action-marker) "and says so in its metadata")
    (is (= [action] (🪐/->dispatch action)))))

(deftest the-bare-call-and-the-wrapped-one-render-the-same
  (let [action (inline/server! (swap! !shared inc))
        render (fn [v] (🪐/on-hooks-replicant->d* {:on {:click v}}
                                                {:serialize-fn identity}))]
    (is (= (render action) (render [action]))
        "so the brackets are concision, not meaning")))

(deftest a-lone-action-keeps-its-modifiers
  (let [action (vary-meta (inline/server! (swap! !shared inc))
                          assoc :datastar/modifiers [:debounce_500ms])
        attrs  (🪐/on-hooks-replicant->d* {:on {:click action}} {})]
    (is (contains? attrs :data-on:click__debounce_500ms)
        "the marker moves the action, and the modifiers move with it")))

(deftest an-inline-body-sits-beside-a-named-action
  (let [action (inline/server! (swap! !shared inc))
        attrs  (🪐/on-hooks-replicant->d* {:on {:click [[:fx/inc] action]}}
                                             {:serialize-fn identity})]
    (is (= [[:fx/inc] [::inline/invoke (second action)]]
           (:actions (:data-intent:click attrs)))
        "one dispatch, one of whose actions is an anonymous body")
    (is (empty? (meta (second (:actions (:data-intent:click attrs)))))
        "and the marker is spent by render time, so nothing transmits it")))
