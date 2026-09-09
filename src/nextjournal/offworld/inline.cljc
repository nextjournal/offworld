(ns nextjournal.offworld.inline
  (:require
   [clojure.string :as str]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.conn :as conn])
  #?(:clj (:import [java.security MessageDigest] [java.util Base64]))
  #?(:cljs (:require-macros [nextjournal.offworld.inline :refer [inline]])))

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

#?(:clj
   (defmacro inline
     "Defer `body` to the server, behind a token in a single registered effect.

  The token is the body's content address when the body's forms are the whole
  of it, and a minted secret when the body reads a local from the surrounding
  scope. The two are indistinguishable on the wire and to the effect, so the
  identity scheme is not part of the intent's vocabulary and a page's served
  vocabulary does not grow with the number of inline sites."
     [& body]
     (if (seq (captured-locals &env body))
       `[[::invoke (register! (fn [] ~@body))]]
       `[[::invoke (derive! ~(form-token body) (fn [] ~@body))]])))

(def invoke ^::🪐/server
  (fn [ctx system tok]
    #?(:clj
       (let [f (or (get @!derived tok)
                   (let [conn-id (or (conn/id (:dispatch-data ctx))
                                     (::🪐/conn-id @system))]
                     (conn/fetch conn-id tok)))]
         (when f (f))))))
