(ns nextjournal.offworld.inline
  (:require
   [clojure.string :as str]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.conn :as conn]
   [nextjournal.offworld.expr :as expr])
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
     token))

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

#?(:clj
   (defn captured-locals
     "The locals of `env` that `forms` reads.

  An over-approximation — a shadowing binding of the same name counts — which
  errs toward minting a token that could have been derived."
     [env forms]
     (let [locals (set (keys env))]
       (into (sorted-set)
             (comp (filter symbol?) (filter locals))
             (tree-seq coll? seq forms)))))

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
   (defn hoist-slots
     "Split `forms` into a body and the client refs it reads.

  The hoist is what makes the rung: every `client!` form leaves the body and
  becomes a named argument, so what stays inside is control flow and nothing
  else. Two consequences fall out. A ref mentioning a local no longer makes the
  body capture anything, since the ref is evaluated at render time, outside it.
  And two sites differing only in which client value they read share one
  address, because the difference is not in the body any more -- a collision
  that is the hoist working rather than a hash being coarse."
     [forms env]
     (let [!refs (atom [])]
       (letfn [(walk [x]
                 (cond
                   (client-marker? x) (let [n (count @!refs)]
                                      (swap! !refs conj (hoisted-ref (rest x) env))
                                      (list `slot n))
                   (map? x)         (into (empty x) (map (fn [[k v]] [(walk k) (walk v)])) x)
                   (map-entry? x)   x
                   (vector? x)      (mapv walk x)
                   (set? x)         (into #{} (map walk) x)
                   (seq? x)         (apply list (map walk x))
                   :else            x))]
         {:body (walk forms)
          :refs @!refs}))))

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
     (let [{:keys [body refs]} (hoist-slots body &env)
           marked (fn [action] `(with-meta ~action {::🪐/action true}))]
       (if (seq (captured-locals &env body))
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

(defn conn-id
  "The connection this body is running for."
  []
  (or (conn/id (:dispatch-data *ctx*))
      (::🪐/conn-id (state))))

(def invoke ^::🪐/server
  (fn [ctx system tok & slots]
    #?(:clj
       (binding [*ctx* ctx *system* system *slots* (vec slots)]
         (let [f (or (get @!derived tok)
                     (conn/fetch (conn-id) tok))]
           (when f (f)))))))
