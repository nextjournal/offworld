(ns nextjournal.offworld-inline-test
  (:require
   [clojure.test :refer [deftest is]]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.divert :as divert]
   [nextjournal.offworld.expr :as expr]
   [clojure.string :as str]
   [nextjournal.offworld.stem :as 🌿]
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
                        (atom {}) {::🪐/conn-id "conn-1"} [action])
        (is (true? @ran) "and runs when the server resolves the token")))))

(deftest the-code-outlives-the-connection-and-the-environment-does-not
  (let [ran (atom 0)]
    (binding [inline/*conn-id* "conn-1"]
      (let [action (inline/server! (swap! ran inc))]
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (nexus/dispatch (nxr/get-registry)
                        (atom {}) {::🪐/conn-id "conn-1"} [action])
        (is (= 1 @ran) "the body runs with what the render held for it")
        (nexus/dispatch (nxr/get-registry)
                        (atom {}) {::🪐/conn-id "conn-2"} [action])
        (is (= 1 @ran) "another connection holds nothing, so nothing happens")
        (inline/release! "conn-1")
        (nexus/dispatch (nxr/get-registry)
                        (atom {}) {::🪐/conn-id "conn-1"} [action])
        (is (= 1 @ran)
            "and the address still resolves once the connection closes -- what expired is the environment")))))

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

(deftest an-address-is-a-name-for-code-and-not-a-permission-to-run-it
  (reset! !outlives 0)
  (nxr/register-system->state! deref)
  (std/register-standard-nexus!)
  (let [action (binding [inline/*conn-id* "conn-9"]
                 (inline/server! (swap! !outlives inc)))]
    (nexus/dispatch (nxr/get-registry)
                    (atom {}) {::🪐/conn-id "conn-8"} [action])
    (is (= 0 @!outlives)
        "a connection that was never handed this body cannot run it, however public its name")
    (nexus/dispatch (nxr/get-registry)
                    (atom {}) {::🪐/conn-id "conn-9"} [action])
    (is (= 1 @!outlives) "the connection the render offered it to can")
    (inline/release! "conn-9")
    (nexus/dispatch (nxr/get-registry)
                    (atom {}) {::🪐/conn-id "conn-9"} [action])
    (is (= 1 @!outlives)
        "and the offer goes when the connection does, though the code itself stays")))

(deftest two-instances-share-one-address-and-differ-only-in-what-they-hold
  (let [seen (atom [])]
    (binding [inline/*conn-id* "conn-1"]
      (let [actions (doall (for [v [:a :b]] (inline/server! (swap! seen conj v))))
            tokens  (map second actions)]
        (is (every? inline/derived-token? tokens)
            "the scope left the body, so the form is the whole of it after all")
        (is (apply = tokens) "and one piece of code has one name, however many times it is rendered")
        (is (apply not= (map #(drop 2 %) actions))
            "what differs between the two is the environment, and only that")
        (is (apply = (map #(nth % 2) actions))
            "and the atom both bodies reach is one thing held once, not one per render")
        (nxr/register-system->state! deref)
        (std/register-standard-nexus!)
        (doseq [action actions]
          (nexus/dispatch (nxr/get-registry)
                          (atom {}) {::🪐/conn-id "conn-1"} [action]))
        (is (= [:a :b] @seen) "each still runs with the value its own render held")))))

(defonce !per-conn (atom {}))

(deftest a-body-reaches-its-connection-without-capturing-it
  (reset! !per-conn {})
  (nxr/register-system->state! deref)
  (std/register-standard-nexus!)
  (let [render  (fn [c] (binding [inline/*conn-id* c]
                          (inline/server! (swap! !per-conn update (inline/conn-id) (fnil inc 0)))))
        actions (into {} (map (juxt identity render)) ["conn-a" "conn-b"])]
    (is (apply = (map (comp second val) actions))
        "two connections render one piece of code, so they render one name for it")
    (doseq [c ["conn-a" "conn-b" "conn-a"]]
      (nexus/dispatch (nxr/get-registry) (atom {}) {::🪐/conn-id c} [(get actions c)]))
    (is (= {"conn-a" 2 "conn-b" 1} @!per-conn)
        "one shared entry, and each dispatch runs for its own connection")))

(defonce !slotted (atom nil))

(deftest a-client-value-rides-beside-the-token
  (let [action      (binding [inline/*conn-id* "conn-s"]
                      (inline/server! (reset! !slotted (inline/client! [:event.target/value]))))
        [k tok ref] action]
    (is (= ::inline/invoke k))
    (is (inline/derived-token? tok) "the ref left the body, so the body is its own whole")
    (is (= [:event.target/value] ref)
        "and the client value the intent reads is visible without running it")
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (nexus/dispatch (nxr/get-registry) (atom {}) {::🪐/conn-id "conn-s"}
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

(deftest a-wrapped-action-passes-through-and-says-its-world
  (let [action (inline/client! [:browser/alert "hi"])]
    (is (= [:browser/alert "hi"] action) "the wrapper claims a world, it does not build one")
    (is (get (meta action) 🪐/action-marker) "and it is one action, so it renders as a dispatch")))

(deftest an-expression-is-named-by-the-generic-key
  (let [action (inline/client! "document.title = 'x'")]
    (is (= [::inline/expr! "document.title = 'x'"] action)
        "one registered effect covers every anonymous client expression there will ever be")))

(deftest a-server-tail-is-lifted-out-beside-the-client-work
  (let [dispatch (inline/client! "document.title = 'x'" (inline/server! (swap! !shared inc)))]
    (is (= 2 (count dispatch)) "siblings, in the order the pipeline runs them")
    (is (= ::inline/expr! (ffirst dispatch)))
    (is (= ::inline/invoke (first (second dispatch))))
    (is (empty? (meta dispatch)) "and a dispatch of two carries no lone-action marker")))

(deftest nothing-client-side-may-follow-a-server-tail
  (let [e (try (macroexpand '(nextjournal.offworld.inline/client!
                              (nextjournal.offworld.inline/server! (println :a))
                              "document.title = 'x'"))
               nil
               (catch Exception e e))]
    (is (some? e) "the order is not a style question, so it fails at macroexpansion")
    (is (re-find #"tail" (ex-message (ex-cause e))))))

(defonce !read (atom nil))

(deftest a-body-reads-an-anonymous-client-value-through-the-generic-key
  (let [[_ _ ref] (inline/server! (reset! !read (inline/client! "document.title")))]
    (is (= [::inline/expr "document.title"] ref)
        "the expression is the placeholder's argument, so the vocabulary stays one key")))

(deftest a-client-body-compiles-to-an-expression
  (let [[k js] (inline/client! (set! document.title "x"))]
    (is (= ::inline/expr! k))
    (is (= "document.title = \"x\"" js) "written as Clojure, sent as the expression it means")))

(deftest a-body-reads-a-compiled-client-value
  (let [[_ _ ref] (inline/server! (reset! !read (inline/client! (= evt.key "Enter"))))]
    (is (= [::inline/expr "((evt.key) === (\"Enter\"))"] ref))))

(deftest the-surrounding-scope-is-spliced-not-sent
  (let [threshold 7
        [_ js]    (inline/client! (> evt.target.value threshold))]
    (is (= "(evt.target.value > 7)" js)
        "a local is a value the render already knows, so it arrives as a literal")))

(deftest a-client-conditional-chooses-between-two-actions
  (let [[k test then else] (inline/client! (if (= evt.key "Enter")
                                            (inline/server! (swap! !shared inc))
                                            (inline/client! "document.blur()")))]
    (is (= ::inline/choose k) "control flow that can be data is data")
    (is (= [::inline/expr "((evt.key) === (\"Enter\"))"] test)
        "only the leaf that tests something is opaque")
    (is (= ::inline/invoke (ffirst then)) "one branch is a server continuation")
    (is (= [::inline/expr! "document.blur()"] (first else))
        "the other is client code, and both are legible without running either")))

(deftest a-registered-test-leaves-nothing-opaque-at-all
  (nxr/register-placeholder! ::dark? (fn [_] true))
  (nxr/register-system->state! deref)
  (std/register-standard-nexus!)
  (reset! !shared 0)
  (let [dispatch (inline/client! (if (inline/client! [::dark?])
                                   (inline/server! (swap! !shared inc))
                                   (inline/client! [:browser/nothing])))]
    (is (= [::dark?] (second dispatch))
        "a named client fact needs no expression, so the whole conditional is data")
    (let [ran (nexus/dispatch (divert/client-nexus (nxr/get-registry)) (atom {}) {} [dispatch])]
      (is (= [[::inline/invoke]] (mapv (comp vector first) (::🪐/server-actions ran)))
          "the client decided and only the branch it chose travelled -- no round trip to decide"))))

(deftest a-name-the-body-binds-is-not-a-name-it-reached-for
  (let [state {:n 1}
        action (inline/server! (swap! !shared (fn [state] (+ state (:n state)))))]
    (is (inline/derived-token? (second action))
        "the inner binding shadows the render's, so nothing was reached for and nothing is held")
    (is (= 2 (count action)) "and no slot was opened for a name that was never free")))

(deftest quoted-data-is-data
  (let [n 3
        action (binding [inline/*conn-id* "conn-q"]
                 (inline/server! (reset! !read (quote (n n n)))))]
    (nxr/register-system->state! deref)
    (std/register-standard-nexus!)
    (nexus/dispatch (nxr/get-registry) (atom {}) {::🪐/conn-id "conn-q"} [action])
    (is (= '(n n n) @!read) "a quoted symbol is not a reference, so it is left alone")))

(deftest a-local-in-head-position-is-held-like-any-other
  (binding [inline/*conn-id* "conn-h"]
    (let [f      (fn [x] (reset! !read x))
          action (inline/server! (f :called))]
      (is (inline/derived-token? (second action)))
      (nxr/register-system->state! deref)
      (std/register-standard-nexus!)
      (nexus/dispatch (nxr/get-registry) (atom {}) {::🪐/conn-id "conn-h"} [action])
      (is (= :called @!read)
          "the function itself is part of the environment, not part of the code"))))

(deftest hoisting-an-environment-needs-somewhere-to-put-it
  (let [x 1]
    (is (thrown? clojure.lang.ExceptionInfo (inline/server! (reset! !read x)))
        "no connection at render time is a loud failure, not a body that reads nils")))

(deftest an-expression-declares-the-runtime-it-needs
  (binding [inline/*conn-id* "conn-r"]
    (let [[_ plain] (inline/client! (.setPointerCapture el (.-pointerId evt)))
          [_ rich]  (inline/client! (clojure.string/join " " (assoc [1] 0 (max 2 3))))]
      (is (empty? (expr/runtime-names plain))
          "the event and the node are in scope, and interop needs no runtime at all")
      (is (= #{"clojure_DOT_string" "squint_core"} (expr/runtime-names rich))
          "anything richer compiles to a call into the compiler's core, and says so in the string")
      (is (re-find #"squint_core\.max" rich)
          "so the ceiling is the compiler's, not a hand-drawn list of allowed forms"))))

(def ^:private a-render-time-constant 30)

(deftest a-name-only-the-render-has-is-refused-rather-than-emitted
  (binding [inline/*conn-id* "conn-v"]
    (let [e (try (macroexpand '(nextjournal.offworld.inline/client!
                                (max nextjournal.offworld-inline-test/a-render-time-constant
                                     evt.movementX)))
                 nil
                 (catch Exception e e))]
      (is (some? e))
      (is (= :server-side-name-in-client-expression (:violation (ex-data (ex-cause e))))
          "a var compiles to a bare identifier the page has never heard of"))
    (let [[_ js] (inline/client! (max ~a-render-time-constant evt.movementX))]
      (is (re-find #"squint_core\.max\(30," js)
          "and saying which stage you meant splices the value the render has"))))

(defn- a-render-time-fn [z] z)

(deftest a-splice-carries-a-value-and-refuses-to-carry-code
  (binding [inline/*conn-id* "conn-w"]
    (is (thrown? clojure.lang.ExceptionInfo
                 (inline/client! (~a-render-time-fn 1)))
        "code has no written form, so splicing it would ship its identity as a string")
    (is (= :unwritable-splice
           (try (inline/client! (~(atom 1)))
                nil
                (catch clojure.lang.ExceptionInfo e (:violation (ex-data e)))))
        "and neither does a reference")))

(defn- a-render-time-helper [a b] (+ a b))

(deftest a-name-the-expression-binds-for-itself-is-its-own
  (binding [inline/*conn-id* "conn-b"]
    (let [[_ js] (inline/client!
                   (let [a-render-time-helper (fn [a b] (+ a b))]
                     (a-render-time-helper 1 evt.movementX)))]
      (is (re-find #"a_render_time_helper" js)
          "an inner binding shadows the var freely, which is the only way to factor a helper in"))
    (is (thrown? Exception
                 (macroexpand '(nextjournal.offworld.inline/client!
                                (nextjournal.offworld-inline-test/a-render-time-helper 1 2))))
        "while calling the render's own fn is still refused")))

(deftest an-alias-is-canonicalised-so-the-page-can-hold-what-it-names
  (binding [inline/*conn-id* "conn-a"]
    (let [[_ js] (inline/client! (str/join "-" ["a" "b"]))]
      (is (re-find #"clojure_DOT_string\.join" js)
          "squint emits the identifier the author wrote, so an alias has to be spelled out")
      (is (= #{"clojure_DOT_string"} (expr/runtime-names js))
          "otherwise the expression declares no runtime, the page holds none, and the click throws"))))

(deftest a-place-travels-as-a-path-and-not-as-the-state-around-it
  (nxr/register-system->state! deref)
  (std/register-standard-nexus!)
  (let [render (fn [n] (binding [inline/*conn-id* "conn-l"]
                         (let [state (🌿/> (🌿/init-state {:box {:w n}}) [:box :w])]
                           (inline/server! (swap! (inline/*local state) inc)))))
        a      (render 120)
        b      (render 140)]
    (is (inline/derived-token? (second a))
        "the stem's path is what the render holds, so the body is still closed")
    (is (= (second a) (second b))
        "and two renders of one place are one address, however the state moved")
    (is (= [:box :w] (nth a 2))
        "what is held is the path, not the whole state a stem carries with it")
    (let [system (atom (🌿/init-state {:box {:w 5}}))]
      (nexus/dispatch (nxr/get-registry) system {::🪐/conn-id "conn-l"} [a])
      (is (= 6 (get-in @system [:box :w])) "and the swap reaches the live state there"))))

(deftest a-literal-path-is-already-a-place
  (binding [inline/*conn-id* "conn-l2"]
    (let [[_ _ at] (inline/server! (reset! (inline/*local [:box :w]) 9))]
      (is (= [:box :w] at) "so it is held as written, with no stem to ask"))))

(deftest a-deref-answers-from-whichever-state-is-at-hand
  (let [state (🌿/> (🌿/init-state {:box {:w 42}}) [:box :w])]
    (is (= 42 @(inline/*local state))
        "at render there is no system, so the stem's own snapshot answers")
    (binding [inline/*system* (atom {:box {:w 7}})]
      (is (= 7 @(inline/*local state))
          "and in a body the live state does, so the same form means the same thing in both"))))
