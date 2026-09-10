(ns nextjournal.offworld.expr
  "A client expression: how one is compiled, and the registered keys that give an
  anonymous one a name.

  An inline body reaches the client the same way anything else does: through a
  registered key with the expression as an argument. One key per kind rather
  than one per site, so a page's served vocabulary does not grow with the number
  of expressions it renders -- the move `invoke` already makes for server
  bodies, on the other side of the boundary.

  What arrives here is a finished client expression: the compiler upstream has
  already decided what is client-side and spliced everything that was not. So
  the compiler here decides what is client-side and splices everything that is
  not, so the evaluator below does nothing but evaluate.

  The compiler is restricted on purpose. Its target is a sandboxed evaluator
  with no library runtime, so the boolean, control and string forms compile to
  bare operators rather than to calls into a compiler's own core. That approach
  and its overrides are taken, with thanks, from `hyper.expr` in
  dynamic-alpha/hyper, which took the transpiler core in turn from Casey Link's
  datastar-expressions (MIT)."
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [nextjournal.offworld.stem :as 🌿]))

#?(:clj
   (defn- bool [e]
     (if (boolean? e) e (vary-meta e assoc :tag 'boolean))))

#?(:clj
   (defn- infix [op args]
     (concat (list (quote js*) (str/join op (repeat (count args) "(~{})"))) args)))

#?(:clj (defn- m-and [_ _ & args] (if (seq args) (bool (infix " && " args)) true)))
#?(:clj (defn- m-or [_ _ & args] (if (seq args) (bool (infix " || " args)) nil)))
#?(:clj (defn- m-not [_ _ x] (bool (concat (list (quote js*) "(!(~{}))") (list x)))))
#?(:clj (defn- m-do [_ _ & args] (if (seq args) (infix ", " args) nil)))
#?(:clj (defn- m-if [_ _ test then else] (list (quote if) (bool test) then else)))
#?(:clj (defn- m-when [_ _ test & body] (list (quote if) (bool test) (cons (quote x/do) body))))
#?(:clj (defn- m-str [_ _ & args] (concat (list (quote js*) (str/join " + " (cons "''" (repeat (count args) "(~{})")))) args)))

#?(:clj
   (defn- m-eq [_ _ & args]
     (let [pairs (partition 2 1 args)]
       (bool (concat (list (quote js*) (str/join " && " (repeat (count pairs) "((~{}) === (~{}))")))
                     (mapcat identity pairs))))))

#?(:clj
   (def ^:private replacements
     {(quote and) (quote x/and)
      (quote or)  (quote x/or)
      (quote not) (quote x/not)
      (quote if)  (quote x/if)
      (quote do)  (quote x/do)
      (quote when) (quote x/when)
      (quote =)   (quote x/=)
      (quote str) (quote x/str)}))

#?(:clj
   (def ^:private overrides
     {(quote x) {(quote and)  m-and
                 (quote or)   m-or
                 (quote not)  m-not
                 (quote do)   m-do
                 (quote if)   m-if
                 (quote when) m-when
                 (quote =)    m-eq
                 (quote str)  m-str}}))

#?(:clj
   (defn- pre-process
     [form]
     (->> form
          (walk/postwalk
           (fn [node]
             (if (and (seq? node) (= (quote not=) (first node)))
               (list (quote not) (cons (quote =) (rest node)))
               node)))
          (walk/postwalk
           (fn [node]
             (if (and (seq? node) (contains? replacements (first node)))
               (cons (get replacements (first node)) (rest node))
               node))))))

(defn js-literal
  "A render-time value as the JavaScript literal standing for it.

  The compiler needs one because a spliced value is not code: it was computed
  on the server before the page shipped, and what reaches the client is its
  written form."
  [v]
  (cond
    (nil? v)     "null"
    (string? v)  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"") (str/replace "\n" "\\n")) "\"")
    (keyword? v) (js-literal (subs (str v) 1))
    (symbol? v)  (js-literal (str v))
    (boolean? v) (str v)
    (number? v)  (str v)
    (map? v)     (str "{" (str/join "," (map (fn [[k val]] (str (js-literal k) ":" (js-literal val))) v)) "}")
    (coll? v)    (str "[" (str/join "," (map js-literal v)) "]")
    (or (fn? v) #?(:clj (instance? clojure.lang.IDeref v) :cljs (satisfies? IDeref v)))
    (throw (ex-info (str "a client expression can carry a value the render computed, but not "
                         (if (fn? v) "a function" "a reference") ": "
                         (pr-str v) " has no written form, so splicing it would put its identity "
                         "on the page as a string. Move the work into a registered client effect "
                         "instead, and name it from client! as a vector.")
                    {:violation :unwritable-splice :value v}))
    :else        (js-literal (str v))))

(defn substitute
  "Fill a compiled template's holes with the values the render computed."
  [template holes]
  (reduce (fn [s [hole v mode]]
            (str/replace s hole (if (= :raw mode) (str v) (js-literal v))))
          template holes))

(def ^:private runtime-reference
  #"\b(squint_core|clojure_DOT_[A-Za-z0-9_$]+)\.")

(defn runtime-names
  "The language runtimes a compiled expression needs by name.

  A compiled expression is mostly operators, but anything richer than an
  operator becomes a call into the compiler's own core, and that core is a
  module the page has to be holding. Derivable from the expression itself, so
  any tool can ask what an intent depends on without being told."
  [js]
  (into (sorted-set) (map second) (re-seq runtime-reference (str js))))

#?(:clj
   (defn- compile-form
     [form]
     (let [compile-string (try (requiring-resolve (quote squint.compiler/compile-string))
                               (catch Exception _
                                 (throw (ex-info "compiling a client! body needs squint on the classpath; a string body needs nothing"
                                                 {:dep (quote io.github.squint-cljs/squint)}))))]
       (-> (compile-string (pr-str (pre-process form))
                           {:elide-imports true
                            :elide-exports true
                            :top-level     false
                            :context       :expr
                            :macros        overrides})
           (str/replace #"squint_core\.truth_\((.*)\)" "!!($1)")
           (str/replace #"\n" " ")
           str/trim
           (str/replace #";$" "")))))

#?(:clj
   (def ^:private compiler-namespaces
     "Namespaces the compiler can emit calls into, given the page holds its
  runtime. A symbol resolving anywhere else names something that exists only
  where the render is."
     #{"clojure.core" "clojure.string" "clojure.set" "clojure.math" "clojure.edn"}))

#?(:clj
   (defn- server-side-var
     [sym]
     (when-let [v (try (resolve sym) (catch Throwable _ nil))]
       (when (var? v)
         (let [vns (some-> v meta :ns ns-name str)]
           (when-not (contains? compiler-namespaces vns)
             v))))))

#?(:clj
   (defn- check-symbol!
     "Refuse a name that would compile to an identifier the browser does not have.

  A symbol the render can see but the page cannot -- anything resolving to a var
  outside the compiler's own namespaces -- otherwise compiles to a bare
  identifier, survives every static check, renders correctly, and throws only on
  the click. Unlike a missing runtime this cannot be fixed by shipping anything,
  because the thing it names lives in the render's process and nowhere else. The
  fix is to say which stage you meant: `~x` splices the value the render has."
     [sym]
     (when-let [v (server-side-var sym)]
       (throw (ex-info (str "a client expression cannot name " sym
                            ", which resolves to " v
                            " -- that exists where the render is, not on the page. "
                            (if (fn? (deref v))
                              (str "It holds code, and code has no written form, so there is "
                                   "nothing to splice: move the work into a registered client "
                                   "effect and name it from client! as a vector.")
                              (str "Splice its value with ~" sym ".")))
                       {:violation :server-side-name-in-client-expression
                        :symbol    sym
                        :var       (str v)})))
     ;; Canonicalize an alias to the namespace it names. Squint emits whatever
     ;; identifier the author wrote, so `str/split` becomes `str.split(...)` --
     ;; a name the page has never heard of, which `runtime-names` cannot see
     ;; because it is looking for a module and this looks like a local. Emitting
     ;; the namespace in full is what makes the compiler and the runtime map
     ;; agree on one name.
     (or (when-let [v (try (resolve sym) (catch Throwable _ nil))]
           (when (var? v)
             (let [vns (some-> v meta :ns ns-name str)]
               (when (and vns (not= "clojure.core" vns) (not= vns (namespace sym)))
                 (symbol vns (name sym))))))
         sym)))

#?(:clj
   (defn- marker-form?
     [x nm]
     (and (seq? x) (symbol? (first x)) (= nm (name (first x))))))

#?(:clj
   (def ^:private expression-binders
     (quote #{let let* loop loop* letfn for doseq dotimes
              if-let when-let if-some when-some})))

#?(:clj
   (defn- bound-syms [pattern]
     (into #{} (filter simple-symbol?) (tree-seq coll? seq pattern))))

#?(:clj
   (defn- infer
     "Decide, form by form, what is client-side and what the render computes.

  Everything is client-side by default -- that is what makes the body read as
  client code. Three things are not, and each leaves a hole the render fills: an
  explicit unquote, a local from the surrounding scope, and a nested `server!`
  or wrapped action, whose value is an action the client-side control flow
  chooses between. A nested `client!` string is neither: it is already client
  code, so it is spliced verbatim.

  Scope is tracked as the walk descends, because a name the expression binds for
  itself is not a name it reached out of the page for -- an inner `fn` or `let`
  may shadow a var freely, and refusing that would forbid the one way to factor
  a helper into an expression at all."
     [form locals !holes]
     (letfn [(hole! [expr mode]
               (let [h (str "__ow_" (count @!holes) "__")]
                 (swap! !holes conj [h expr mode])
                 (symbol h)))
             (binder [head node bound]
               (if (#{"fn" "fn*"} (name head))
                 (let [[_ & more] node
                       [nm more]  (if (symbol? (first more)) [(first more) (rest more)] [nil more])
                       bound      (cond-> bound nm (conj nm))
                       arity      (fn [[params & body]]
                                    (let [b (into bound (bound-syms params))]
                                      (cons params (map #(xf % b) body))))]
                   (concat (list head) (when nm [nm])
                           (if (vector? (first more))
                             (arity more)
                             (map arity more))))
                 (let [[_ bindings & body] node
                       [pairs bound']
                       (reduce (fn [[acc bound] [pattern expr]]
                                 (if (keyword? pattern)
                                   [(conj acc pattern (xf expr bound)) bound]
                                   [(conj acc pattern (xf expr bound))
                                    (into bound (bound-syms pattern))]))
                               [[] bound]
                               (partition 2 bindings))]
                   (list* head (vec pairs) (map #(xf % bound') body)))))
             (xf [node bound]
               (cond
                 (marker-form? node "unquote")
                 (hole! (second node) :value)

                 ;; (el path-or-stem) -- the render derives the id, so what
                 ;; reaches the page is a plain lookup by a literal name rather
                 ;; than an id string threaded through every render-fn on the way
                 ;; down. Legible in the compiled expression, which a call into
                 ;; something of ours would not be.
                 (marker-form? node "el")
                 (list (quote js/document.getElementById)
                       (hole! `(🌿/id ~(second node)) :value))

                 (and (marker-form? node "client!") (string? (second node)))
                 (hole! (second node) :raw)

                 ;; An action spliced into the middle of an expression becomes a
                 ;; JavaScript array and nothing dispatches it. Nor could it mean
                 ;; anything if it did: the expression runs to completion before
                 ;; the server-bound remainder is posted, so a server! here can
                 ;; only mean "also send this" -- what a tail form already means
                 ;; -- while reading as though it were sequenced. Tail position
                 ;; and a conditional's branches are the two places an action is
                 ;; selected rather than sequenced, and both are handled before
                 ;; anything reaches here.
                 (or (marker-form? node "server!") (marker-form? node "client!"))
                 (throw (ex-info (str "an action cannot sit inside a client expression: "
                                      (pr-str (first node))
                                      " here would compile to a value and never be dispatched."
                                      " Put it in tail position, where it becomes a sibling"
                                      " action, or in a branch of an `if`/`when` whose test is"
                                      " the client fact -- those are the two positions that"
                                      " select an action rather than sequence one.")
                                 {:violation :action-inside-client-expression
                                  :marker    (first node)}))

                 (and (seq? node) (symbol? (first node))
                      (or (contains? expression-binders (symbol (name (first node))))
                          (#{"fn" "fn*"} (name (first node)))))
                 (binder (first node) node bound)

                 (and (vector? node) (keyword? (first node)))
                 (throw (ex-info (str "a reference cannot appear inside a client expression: "
                                      (pr-str (first node))
                                      " names something a stage resolves, and a compiled expression "
                                      "has no stage left to resolve it at. Hoist it into a slot instead, "
                                      "or splice a render-time value with ~")
                                 {:violation :reference-in-client-expression
                                  :key       (first node)}))

                 (and (seq? node) (keyword? (first node)))
                 (hole! node :value)

                 (seq? node)
                 (let [[head & args] node]
                   (cons (cond
                           (not (symbol? head))          (xf head bound)
                           (contains? bound head)        head
                           :else                         (check-symbol! head))
                         (map #(xf % bound) args)))

                 (vector? node) (mapv #(xf % bound) node)
                 (map? node)    (into {} (map (fn [[k v]] [(xf k bound) (xf v bound)])) node)
                 (set? node)    (into #{} (map #(xf % bound)) node)

                 (and (symbol? node) (contains? locals node))
                 (hole! node :value)

                 (and (symbol? node) (not (contains? bound node)))
                 (check-symbol! node)

                 :else node))]
       (xf form #{}))))

#?(:clj
   (defn compile-expression
     "A client expression compiled from `forms`, plus the holes the render fills."
     [forms env]
     (let [!holes (atom [])
           forms' (mapv #(infer % (set (keys env)) !holes) forms)]
       {:template (str/join ", " (map compile-form forms'))
        :holes    @!holes})))

#?(:cljs (defonce ^:private !compiled (atom {})))

#?(:cljs (defonce !runtimes (atom {})))

#?(:cljs
   (defn register-runtime!
     "Make a language runtime reachable from client expressions, by the name the
  compiler emits for it -- `squint_core`, `clojure_DOT_string`.

  The page has to hold these, because a compiled expression is evaluated with
  nothing but what it is handed. A host that bundles the compiler's core can
  register it here, or leave it on `globalThis.offworldRuntimes` under the same
  names and it is picked up lazily, which is what a plugin loaded before this
  namespace has to do."
     [nm module]
     (swap! !runtimes assoc nm module)))

#?(:cljs
   (defn- runtime-module
     [nm]
     (or (get @!runtimes nm)
         (some-> (aget js/globalThis "offworldRuntimes") (aget nm)))))

#?(:cljs
   (defn- compiled
     [js needed]
     (or (get @!compiled js)
         (let [f (.apply js/Function nil
                         (to-array (concat ["evt" "el"] needed
                                           [(str "return (" js ");")])))]
           (swap! !compiled assoc js f)
           f))))

#?(:cljs
   (defn- evaluate
     [dispatch-data js]
     (let [{:replicant/keys [dom-event node]} dispatch-data
           needed  (vec (runtime-names js))
           modules (mapv runtime-module needed)]
       (when-let [missing (seq (keep (fn [[nm m]] (when-not m nm)) (map vector needed modules)))]
         (throw (ex-info (str "a client expression needs " (str/join ", " missing)
                              " and the page is not holding it -- register it with "
                              "nextjournal.offworld.expr/register-runtime!, or expose it as "
                              "globalThis.offworldRuntimes")
                         {:violation :runtime-missing :needed needed :expression js})))
       (.apply (compiled js needed) nil (to-array (concat [dom-event node] modules))))))

(def expr
  "A client value the intent reads, named by the expression that produces it."
  (fn [dispatch-data js]
    #?(:cljs (evaluate dispatch-data js))))

(def expr!
  "A client effect, named by the expression that performs it."
  (fn [ctx _system js]
    #?(:cljs (evaluate (:dispatch-data ctx) js))))

(defn as-dispatch
  "One action or several, as several."
  [v]
  (if (keyword? (first v)) [v] (vec v)))

(def choose
  "A decision made on the client, with both outcomes left as data.

  An expansion is handed no event, so the condition cannot be evaluated here --
  which is the constraint that makes this the right shape rather than a taste.
  The condition arrives already resolved, by a placeholder that did have the
  event, and what stays is a choice between two dispatches nothing had to run to
  read. So the only opaque part of a client-side conditional is the leaf that
  tests something, and a reader can still see both of the things it decides
  between."
  (fn [_state test then else]
    (as-dispatch (if test then else))))

(defn expression?
  "Whether `x` is a client expression rather than an action or a value."
  [x]
  (string? x))
