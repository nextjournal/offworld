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
   [clojure.walk :as walk]))

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
    :else        (js-literal (str v))))

(defn substitute
  "Fill a compiled template's holes with the values the render computed."
  [template holes]
  (reduce (fn [s [hole v mode]]
            (str/replace s hole (if (= :raw mode) (str v) (js-literal v))))
          template holes))

#?(:clj
   (def ^:private runtime-reference
     #"(squint_core|[A-Za-z0-9_$]*_DOT_[A-Za-z0-9_$.]*)\."))

#?(:clj
   (defn- no-runtime!
     "Refuse an expression that compiled to a call into a language runtime.

  The target is a sandboxed evaluator holding nothing but the page, so a
  reference to a compiler's own core -- or to a namespace it munged -- is a name
  that will not exist when the expression runs. Left alone it compiles happily,
  renders happily, reads back happily in any static analysis, and then throws in
  the browser on the click, which is the worst place to find out. So it is an
  error here instead.

  What to do about it is not to work around the compiler: reach for a registered
  client effect, whose body is ordinary ClojureScript with a whole runtime behind
  it, and name it from `client!` as a vector."
     [js form]
     (if-let [m (re-find runtime-reference js)]
       (throw (ex-info (str "a client expression cannot call into a language runtime, and "
                            (pr-str form) " compiled to " (pr-str (first m))
                            " -- the browser has no such name. Use a registered client effect "
                            "for anything needing more than operators, and name it from client! "
                            "as a vector.")
                       {:violation :runtime-reference-in-client-expression
                        :form      form
                        :compiled  js}))
       js)))

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
           (str/replace #";$" "")
           (no-runtime! form)))))

#?(:clj
   (defn- marker-form?
     [x nm]
     (and (seq? x) (symbol? (first x)) (= nm (name (first x))))))

#?(:clj
   (defn- infer
     "Decide, form by form, what is client-side and what the render computes.

  Everything is client-side by default -- that is what makes the body read as
  client code. Three things are not, and each leaves a hole the render fills: an
  explicit unquote, a local from the surrounding scope, and a nested `server!`
  or wrapped action, whose value is an action the client-side control flow
  chooses between. A nested `client!` string is neither: it is already client
  code, so it is spliced verbatim."
     [form locals !holes]
     (letfn [(hole! [expr mode]
               (let [h (str "__ow_" (count @!holes) "__")]
                 (swap! !holes conj [h expr mode])
                 (symbol h)))
             (xf [node]
               (cond
                 (marker-form? node "unquote")
                 (hole! (second node) :value)

                 (and (marker-form? node "client!") (string? (second node)))
                 (hole! (second node) :raw)

                 (or (marker-form? node "server!") (marker-form? node "client!"))
                 (hole! node :value)

                 (and (seq? node) (keyword? (first node)))
                 (hole! node :value)

                 (seq? node)
                 (let [[head & args] node]
                   (cons (if (symbol? head) head (xf head)) (map xf args)))

                 (and (vector? node) (keyword? (first node)))
                 (throw (ex-info (str "a reference cannot appear inside a client expression: "
                                      (pr-str (first node))
                                      " names something a stage resolves, and a compiled expression "
                                      "has no stage left to resolve it at. Hoist it into a slot instead, "
                                      "or splice a render-time value with ~")
                                 {:violation :reference-in-client-expression
                                  :key       (first node)}))

                 (vector? node) (mapv xf node)
                 (map? node)    (into {} (map (fn [[k v]] [(xf k) (xf v)])) node)
                 (set? node)    (into #{} (map xf) node)

                 (and (symbol? node) (contains? locals node))
                 (hole! node :value)

                 :else node))]
       (xf form))))

#?(:clj
   (defn compile-expression
     "A client expression compiled from `forms`, plus the holes the render fills."
     [forms env]
     (let [!holes (atom [])
           forms' (mapv #(infer % (set (keys env)) !holes) forms)]
       {:template (str/join ", " (map compile-form forms'))
        :holes    @!holes})))

#?(:cljs (defonce ^:private !compiled (atom {})))

#?(:cljs
   (defn- compiled
     [js]
     (or (get @!compiled js)
         (let [f (js/Function. "evt" "el" (str "return (" js ");"))]
           (swap! !compiled assoc js f)
           f))))

#?(:cljs
   (defn- evaluate
     [dispatch-data js]
     (let [{:replicant/keys [dom-event node]} dispatch-data]
       ((compiled js) dom-event node))))

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
