(ns nextjournal.offworld.inline
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.claim :as claim]
   [nextjournal.offworld.conn :as conn]
   [nextjournal.offworld.expr :as expr]
   [nextjournal.offworld.stem :as 🌿])
  #?(:clj (:import [java.security MessageDigest] [java.util Base64]))
  #?(:cljs (:require-macros [nextjournal.offworld.inline :refer [server!]])))

#?(:clj (def ^:dynamic *conn-id* nil))

#?(:clj (defonce !derived (atom {})))

#?(:clj
   (defn register!
     "Hold `f` for the current connection under a freshly minted token.

  The fallback identity, for a body whose forms are not the whole of it: the
  token says nothing about what it stands for, so it cannot be shared, cannot
  outlive the connection, and has to be swept."
     [f]
     (binding [conn/*conn-id* (or *conn-id* conn/*conn-id*)]
       (conn/stash! f))))

#?(:clj
   (defn hold!
     "Render-time. Hold `v` for the current connection, for a body hoisted out of
  the scope that had it, and return the reference that resolves back to it."
     [v]
     (let [id (or *conn-id* conn/*conn-id*)]
       (when-not id
         (throw (ex-info "an inline body hoists its environment, so rendering one needs a connection"
                         {:held (type v)})))
       (binding [conn/*conn-id* id]
         (claim/hold! v)))))

#?(:clj (defn release! [conn-id] (conn/release! conn-id)))

#?(:clj
   (defn derive!
     "Hold `f` under `token`, once.

  Idempotent because the token is derived from the body's source rather than
  minted, so every render of a site presents the same one. What accumulates is
  one entry per source site — a quantity the codebase fixes — which is why
  nothing here evicts."
     [token f]
     (swap! !derived (fn [m] (if (contains? m token) m (assoc m token f))))
     (binding [conn/*conn-id* (or *conn-id* conn/*conn-id*)]
       (conn/offer! token))))

(defn derived-token?
  "Whether `token` names a body by its form rather than by a minted secret."
  [token]
  (and (string? token) (str/starts-with? token "sha256.")))

#?(:clj
   (defn- gensym-name?
     "Whether `sym` was named by the reader or a macro rather than by an author.

  Auto-gensyms carry a counter that changes every time the form is read, so
  they are the one part of a body's source that is not stable across processes."
     [sym]
     (let [n (name sym)]
       (or (str/ends-with? n "#")
           (boolean (re-matches #".*__\d+(__auto__)?" n))))))

#?(:clj
   (defn- canonicalize
     "Rewrite `forms` so equal source reads as equal data.

  Machine-named symbols are renumbered in first-appearance order, which makes
  the hash blind to when the form was read and to which macro expanded it.
  Author-named locals are left alone: two bodies that differ only in the name
  of a local hash apart, missing a dedup rather than claiming a false one."
     [forms]
     (let [!seen (atom {})
           rename (fn [sym]
                    (or (get @!seen sym)
                        (get (swap! !seen assoc sym (symbol (str "g_" (count @!seen)))) sym)))]
       (letfn [(walk [x]
                 (cond
                   (and (symbol? x) (gensym-name? x)) (rename x)
                   (map? x) (into (empty x) (map (fn [[k v]] [(walk k) (walk v)])) x)
                   (map-entry? x) x
                   (vector? x) (mapv walk x)
                   (set? x) (into #{} (map walk) x)
                   (seq? x) (apply list (map walk x))
                   :else x))]
         (walk forms)))))

#?(:clj
   (defn- digest [^String s]
     (-> (Base64/getUrlEncoder)
         (.withoutPadding)
         (.encodeToString (.digest (MessageDigest/getInstance "SHA-256")
                                   (.getBytes s "UTF-8"))))))

#?(:clj
   (defn form-token
     "The content address of `forms`: a compile-time constant standing for a body."
     [forms]
     (str "sha256." (digest (pr-str (canonicalize forms))))))

(def ^:dynamic *slots* nil)

(defn slot
  "The value of the inline body's `n`th hoisted slot.

  What `client!` expands to. The body reads a client value here rather than
  closing over one, because a closure cannot close over a fact that does not
  exist yet: the value is read on the client, at dispatch time, and arrives as
  an argument."
  [n]
  (nth *slots* n))

#?(:clj
   (defn- client-marker?
     "Whether `x` is a `client!` call.

  Matched by name rather than by resolution, so it holds under every alias and
  under a transpiler with no vars to resolve against."
     [x]
     (and (seq? x) (symbol? (first x)) (= "client!" (name (first x))))))

#?(:clj
   (defn- local-marker?
     "Whether `x` is `(local <stem>)` -- the one form whose argument the render
  evaluates rather than the body, so that a place travels as a path and not as
  the whole state a stem carries."
     [x]
     (and (seq? x) (symbol? (first x)) (= "*local" (name (first x)))
          (= 2 (count x)))))

#?(:clj
   (defn- server-form?
     [x]
     (and (seq? x) (symbol? (first x)) (= "server!" (name (first x))))))

#?(:clj
   (defn client-form
     "What one `client!` form denotes: an action, or the expression that names one.

  Three arms, told apart by what the author wrote rather than by where it sits.
  A vector is an action already and passes through unchanged -- the wrapper
  claims a world rather than building one, which is the whole of its work. A
  string is a client expression verbatim, the escape hatch, and the one arm
  nothing downstream can analyze. Anything else is Clojure meant for the
  client, and the compiler decides what of it crosses."
     [forms]
     (let [x (first forms)]
       (cond
         (and (= 1 (count forms)) (vector? x)) {:action x}
         (and (= 1 (count forms)) (string? x)) {:expression x}
         :else                                 {:body (vec forms)}))))

#?(:clj
   (defn- compiled
     "The form that produces a client expression at render time.

  A body with no holes is already a string when the macro finishes, so the
  render does nothing at all. A body reading the surrounding scope leaves the
  holes the render fills, which is the same hoist the slots make, one level
  down: what crosses is a value, and the code that reads it never left."
     [body env]
     (let [{:keys [template holes]} (expr/compile-expression body env)]
       (if (seq holes)
         `(expr/substitute ~template [~@(map (fn [[h e mode]] [h e mode]) holes)])
         template))))

#?(:clj
   (defn- hoisted-ref
     "The reference a `client!` inside a body becomes.

  A value the body reads is a placeholder either way: an authored one when the
  author named a registered key, and the generic expression key when they wrote
  an expression instead. Which is why an anonymous client value costs no new
  vocabulary -- the anonymity rides as the key's argument."
     [forms env]
     (let [{:keys [action expression body]} (client-form forms)]
       (cond
         action     action
         expression [::expr expression]
         :else      `[::expr ~(compiled body env)]))))

#?(:clj
   (defn- action-form?
     [x]
     (or (server-form? x) (client-marker? x))))

#?(:clj
   (defn- conditional
     "A client-side decision whose outcomes are actions, kept as data.

  Recognised rather than compiled, because a conditional between two actions is
  the one piece of client control flow that does not have to be opaque: the test
  becomes a placeholder and the branches stay a pair of dispatches. Reach for
  the compiler only for what is left."
     [forms env]
     (let [[x] forms]
       (when (and (= 1 (count forms))
                  (seq? x)
                  (symbol? (first x))
                  (#{"if" "when"} (name (first x)))
                  (some action-form? (rest x)))
         (let [[_ test then else] x]
           (when (server-form? test)
             (throw (ex-info "a client-side decision cannot test a server value: that is the split this rung exists to avoid"
                             {:test test})))
           `[::choose ~(if (client-marker? test)
                         (hoisted-ref (rest test) env)
                         `[::expr ~(compiled [test] env)])
             (expr/as-dispatch ~(or then []))
             (expr/as-dispatch ~(or else []))])))))

#?(:clj
   (defmacro client!
     "The client's half of an intent, said out loud at the call site.

  Two positions, and the position is what settles the kind, so neither has to
  be named. Inside a `server!` body it is a value the body reads: the form is
  hoisted out before the body is a body at all, and the client resolves it
  ahead of the request. At the top of a dispatch it is a client effect, which
  runs in the client's own dispatch stage -- before the request too, since that
  is the only place a client effect can run at all.

  A `server!` in tail position is lifted out beside it rather than nested,
  because that is what the pipeline does with it: the client's work happens,
  then the remainder travels. Writing them as siblings says so; nesting them
  would only look like it meant something else."
     [& forms]
     (let [client-part (vec (take-while (complement server-form?) forms))
           server-tail (vec (drop-while (complement server-form?) forms))
           _           (when-not (every? server-form? server-tail)
                         (throw (ex-info "a server! inside client! is the tail, so nothing client-side may follow it"
                                         {:after (remove server-form? server-tail)})))
           {:keys [action expression body]} (client-form client-part)
           client-action (cond
                           action     action
                           expression `[::expr! ~expression]
                           (seq body) (or (conditional body &env)
                                          `[::expr! ~(compiled body &env)]))]
       (cond
         (and client-action (seq server-tail))
         `[~client-action ~@server-tail]

         client-action
         `(with-meta ~client-action {::🪐/action true})

         :else
         `[~@server-tail]))))

#?(:clj
   (defn- elide-render-time
     "Drop the subtrees whose symbols are not references from inside the body.

  A `client!` form is evaluated where the render is, so the names in it are the
  render's own and not free in the body at all. A quoted form is data."
     [forms]
     (walk/prewalk
      (fn [x]
        (if (or (client-marker? x) (and (seq? x) (= (quote quote) (first x))))
          nil
          x))
      forms)))

#?(:clj
   (defn- free-in
     [node locals bound]
     (letfn [(binds [pattern] (into #{} (filter symbol?) (tree-seq coll? seq pattern)))
             (fs [node bound]
               (cond
                 (and (seq? node) (= (quote quote) (first node))) #{}

                 (and (seq? node) (#{(quote let*) (quote loop*)} (first node)))
                 (let [[_ bindings & body] node
                       [free bound']
                       (reduce (fn [[free bound] [sym expr]]
                                 [(into free (fs expr bound)) (conj bound sym)])
                               [#{} bound]
                               (partition 2 bindings))]
                   (into free (mapcat #(fs % bound')) body))

                 (and (seq? node) (= (quote fn*) (first node)))
                 (let [[_ & more] node
                       [nm more]  (if (symbol? (first more)) [(first more) (rest more)] [nil more])
                       bound      (cond-> bound nm (conj nm))
                       arities    (if (vector? (first more)) [more] more)]
                   (into #{}
                         (mapcat (fn [[params & body]]
                                   (let [b (into bound (binds params))]
                                     (mapcat #(fs % b) body))))
                         arities))

                 (and (seq? node) (= (quote letfn*) (first node)))
                 (let [[_ bindings & body] node
                       bound' (into bound (take-nth 2 bindings))]
                   (into (into #{} (mapcat #(fs % bound')) (take-nth 2 (rest bindings)))
                         (mapcat #(fs % bound'))
                         body))

                 (and (seq? node) (= (quote catch) (first node)))
                 (let [[_ _klass sym & body] node]
                   (into #{} (mapcat #(fs % (conj bound sym))) body))

                 (map? node)  (into #{} (mapcat (fn [[k v]] (into (fs k bound) (fs v bound)))) node)
                 (coll? node) (into #{} (mapcat #(fs % bound)) (seq node))

                 (and (symbol? node) (contains? locals node) (not (contains? bound node)))
                 #{node}

                 :else #{}))]
       (fs node bound))))

#?(:clj
   (defn free-locals
     "The names `forms` reaches out of its own scope for.

  Answered on the macroexpanded form, because there the set of forms that can
  bind a name is small and closed -- four special forms and a catch clause --
  where on source it is however many binding macros exist. The rewrite is done
  on the source, so the two are compared before the result is trusted: they
  agree for anything the rewrite understood, and a disagreement is what makes it
  fall back rather than guess."
     [forms env]
     (let [locals (set (keys env))]
       (if (empty? locals)
         #{}
         (free-in (walk/macroexpand-all (elide-render-time forms)) locals #{})))))

#?(:clj
   (def ^:private pair-binders
     "Forms whose second element is a vector of pattern/expression pairs."
     (quote #{let let* loop loop* if-let when-let if-some when-some with-open
              doseq for})))

#?(:clj
   (defn- pattern-syms
     "Every name a binding pattern could introduce.

  Over-approximated on purpose -- a default in an `:or` map contributes its
  symbols too. Naming too much as bound only ever means a value is left in the
  body instead of hoisted out of it, which costs a derived address and never
  costs correctness."
     [pattern]
     (into #{} (filter simple-symbol?) (tree-seq coll? seq pattern))))

#?(:clj
   (defn hoist-slots
     "Split `forms` into a closed body and the references it used to reach out with.

  Two kinds of reach, and both leave the body: a `client!` form, which the client
  resolves before the request, and a plain local from the surrounding render,
  which the server resolves at dispatch from a value the render held. What is
  left is a *closed* term -- no free variable, nothing implicit -- which is what
  makes its content address a complete identity rather than a pointer that needs
  an environment beside it to mean anything.

  Scope is tracked as the walk descends, so a name the body binds for itself is
  not a name it reached out for. That distinction is the whole reason this can
  hoist at all: the earlier over-approximation could only ever answer *did the
  body mention this name*, which is not the question."
     [forms env]
     (let [!refs   (atom [])
           !held   (atom {})
           locals  (set (keys env))
           add!    (fn [ref] (let [n (count @!refs)] (swap! !refs conj ref) (list `slot n)))
           hold!   (fn [sym] (or (get @!held sym)
                                 (let [s (add! `(hold! ~sym))]
                                   (swap! !held assoc sym s)
                                   s)))]
       (letfn [(binder-body [head node bound]
                 (case (name head)
                   ("fn" "fn*") (fn-form head node bound)
                   "letfn"      (letfn-form head node bound)
                   "catch"      (let [[_ klass sym & body] node]
                                  (list* head klass sym (map #(w % (conj bound sym)) body)))
                   (pairs-form head node bound)))

               (pairs-form [head node bound]
                 (let [[_ bindings & body] node
                       [pairs bound']
                       (reduce (fn [[acc bound] [pattern expr]]
                                 (if (and (keyword? pattern) (not= :let pattern))
                                   [(conj acc pattern (w expr bound)) bound]
                                   (let [[pattern' bound']
                                         (if (= :let pattern)
                                           [pattern bound]
                                           [pattern (into bound (pattern-syms pattern))])]
                                     [(conj acc pattern' (w expr bound))
                                      (if (= :let pattern)
                                        (into bound' (pattern-syms (take-nth 2 expr)))
                                        bound')])))
                               [[] bound]
                               (partition 2 bindings))]
                   (list* head (vec pairs) (map #(w % bound') body))))

               (arity [node bound]
                 (let [[params & body] node
                       bound' (into bound (pattern-syms params))]
                   (list* params (map #(w % bound') body))))

               (fn-form [head node bound]
                 (let [[_ & more]   node
                       [nm more]    (if (symbol? (first more)) [(first more) (rest more)] [nil more])
                       bound        (cond-> bound nm (conj nm))
                       arities      (if (vector? (first more)) [more] more)
                       done         (map #(arity % bound) arities)]
                   (concat (list head) (when nm [nm])
                           (if (vector? (first more)) (first done) done))))

               (letfn-form [head node bound]
                 (let [[_ fns & body] node
                       bound          (into bound (map first fns))]
                   (list* head
                          (vec (map (fn [f] (cons (first f) (arity (rest f) bound))) fns))
                          (map #(w % bound) body))))

               (w [node bound]
                 (cond
                   (and (seq? node) (= (quote quote) (first node))) node

                   (local-marker? node)
                   (let [arg (second node)
                         ;; a stem knows its own path; a literal path is already one
                         at  (if (vector? arg) arg `(🌿/path ~arg))]
                     ;; the argument is the render's to evaluate, so whatever names
                     ;; it counts as reached-for and the two analyses must agree
                     (doseq [sym (filter locals (tree-seq coll? seq arg))]
                       (swap! !held assoc sym ::at-a-path))
                     (list `*local (add! at)))

                   (client-marker? node) (add! (hoisted-ref (rest node) env))

                   (and (seq? node) (symbol? (first node))
                        (or (contains? pair-binders (symbol (name (first node))))
                            (#{"fn" "fn*" "letfn" "catch"} (name (first node)))))
                   (binder-body (first node) node bound)

                   (map? node)    (into (empty node) (map (fn [[k v]] [(w k bound) (w v bound)])) node)
                   (map-entry? node) node
                   (vector? node) (mapv #(w % bound) node)
                   (set? node)    (into #{} (map #(w % bound)) node)
                   (seq? node)    (apply list (map #(w % bound) node))

                   (and (symbol? node) (contains? locals node) (not (contains? bound node)))
                   (hold! node)

                   :else node))]
         (let [body (mapv #(w % #{}) forms)]
           {:body body
            :refs @!refs
            :held (set (keys @!held))})))))

#?(:clj
   (defmacro server!
     "Defer `body` to the server, behind a token in a single registered effect.

  The token is the body's content address when the body's forms are the whole
  of it, and a minted secret when the body reads a local from the surrounding
  scope. The two are indistinguishable on the wire and to the effect, so the
  identity scheme is not part of the intent's vocabulary and a page's served
  vocabulary does not grow with the number of inline sites.

  Every `client!` form in `body` is hoisted into a slot beside the token, which
  is the one thing this rung has that the closure above it does not: the client
  values the intent reads are visible without running it.

  Expands to one action rather than a whole dispatch, marked so the render
  preprocessor knows to wrap it. So it reads as the sole handler where that is
  all there is, and sits beside named data actions in one dispatch where it is
  not -- without teaching Replicant's `:on` convention a second shape."
     [& body]
     (let [free                     (free-locals body &env)
           {:keys [refs held] :as h} (hoist-slots body &env)
           body                      (:body h)
           marked (fn [action] `(with-meta ~action {::🪐/action true}))]
       (if (not= free held)
         (marked `[::invoke (register! (fn [] ~@body)) ~@refs])
         (marked `[::invoke (derive! ~(form-token body) (fn [] ~@body)) ~@refs])))))

(def ^:dynamic *ctx* nil)

(def ^:dynamic *system* nil)

(defn state
  "The system state, for a body that runs on the server without capturing it.

  Ambient at invoke time rather than hoisted, because the dispatch context is
  the one thing an inline body needs that must not cross the boundary: reading
  it through a binding is what lets a body reach per-connection state and still
  be wholly described by its forms."
  []
  (some-> *system* deref))

(defn system
  "The system itself, for a body that writes to it without capturing it."
  []
  *system*)

#?(:clj
   (deftype Local [path snapshot]
     clojure.lang.IDeref
     (deref [_] (if *system*
                  (get-in @*system* path)
                  (get-in snapshot path)))
     clojure.lang.IAtom
     (swap [_ f] (get-in (swap! *system* update-in path f) path))
     (swap [_ f a] (get-in (swap! *system* update-in path f a) path))
     (swap [_ f a b] (get-in (swap! *system* update-in path f a b) path))
     (swap [_ f a b args] (get-in (apply swap! *system* update-in path f a b args) path))
     (reset [_ v] (get-in (swap! *system* assoc-in path v) path))
     (compareAndSet [_ old new]
       (if (= old (get-in @*system* path))
         (do (swap! *system* assoc-in path new) true)
         false))))

(defn el
  "The element a path names, looked up on the page.

  Only means anything inside a `client!` expression, where `server!` rewrites it:
  the render derives the id from the path with `stem/id` and the expression
  carries a plain `document.getElementById(\"...\")` with that name in it. So a
  render-fn passes the *path* it already has rather than threading an id string
  down beside it, and what the page evaluates is still a lookup anyone can read."
  [path-or-stem]
  (throw (ex-info "el names an element for a client expression, so it means nothing outside one"
                  {:at path-or-stem})))

(defn *local
  "The system, narrowed to one place in it: `swap!`, `reset!` and `deref` all
  reach that place and nothing else.

  Give it a **stem** and it derives the place the stem already names -- the path
  the component was handed, so a render-fn writes where it reads without either
  of them restating a path. Give it a **vector** and that is the place.

  Reads answer from whichever state is at hand: the live system when a body is
  running, and the render's own snapshot otherwise, so `@*local` means the same
  thing in a view and in the intent the view emits. It is not a cursor the
  program keeps -- it lives for one write, and a place never becomes a value
  that outlives the intent that meant it.

  `server!` rewrites the call so a *stem* argument contributes only its path to
  the body. A stem carries the whole state map with it, and holding that would
  mint a claim per render instead of one per place."
  [at]
  #?(:clj (if (map? at)
            (->Local (vec (🌿/path at)) (🌿/stem at))
            (->Local (vec at) nil))))

(defn conn-id
  "The connection this body is running for."
  []
  (or (conn/id (:dispatch-data *ctx*))
      (::🪐/conn-id (state))))

(def invoke ^::🪐/server
  (fn [ctx system tok & slots]
    #?(:clj
       (binding [*ctx* ctx *system* system *slots* (vec slots)]
         (let [id (conn-id)
               f  (if (derived-token? tok)
                    (when (conn/offered? id tok) (get @!derived tok))
                    (conn/fetch id tok))]
           (when f (f)))))))
