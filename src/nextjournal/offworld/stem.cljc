(ns nextjournal.offworld.stem
  (:refer-clojure :exclude [+ >])
  #?(:cljs
     (:require-macros
      [nextjournal.offworld.stem :refer [trace trace-me defq]]))
  (:require
   [clojure.string :as str]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.stem :as-alias 🌿]
   [nexus.registry :as nxr]))

(defn ->v [x] (if (sequential? x) (into [] x) [x]))

(defn init-state [state]
  (merge state {::🌿/stem state}))

(defn stem [state] (::🌿/stem state))

(defn path
  ([state] (path state []))
  ([state suffix] (into (::🌿/path state [::🌿/local])
                        (->v suffix))))

(def id-separator "--")

(defn- segment-name [x]
  (if-let [ns' (and (keyword? x) (namespace x))]
    (str (str/replace ns' "." "-") "_" (name x))
    (if (keyword? x) (name x) (str x))))

(defn- slug [s]
  (str/replace s #"[^A-Za-z0-9_-]+" "-"))

(defn id
  "The element id denoting a stem path.

  A path joined and sanitized rather than encoded, because nothing reads an id
  back: `el` recomputes it from the same path. A keyword contributes its whole
  namespace, because a namespace is what keeps two paths apart and abbreviating
  it invites exactly the collision this scheme cannot rule out; the ids get long
  and the wire is compressed. The namespace-name boundary stays visible as `_`
  where a dot is `-`, so the two are not run together. The charset is chosen for
  where an id is *used* — `[A-Za-z0-9_-]` is what a CSS identifier allows
  unescaped, which is what makes an id usable in a selector and in the dashed
  ident an anchor name needs. Encoding it bought reversibility nobody wanted
  and cost exactly that.

  Sanitizing can in principle collide, where an encoding could not. That is a
  naming mistake rather than a mechanism, so it is detected in a render instead
  of paid for in every id."
  ([path-or-state] (id path-or-state []))
  ([path-or-state suffixes]
   (if (map? path-or-state)
     (id (path path-or-state) suffixes)
     (let [joined (->> (into (->v path-or-state) suffixes)
                       (map (comp slug segment-name))
                       (str/join id-separator))]
       (cond->> joined
         (re-find #"^[0-9-]" joined) (str "id-"))))))

(def el ^::🪐/client
  (fn [_ path-or-id]
    #?(:cljs
       (js/document.getElementById
        (cond
          (string? path-or-id)     path-or-id
          (sequential? path-or-id) (id path-or-id))))))

(defn local [m] (get-in (stem m) (path m)))

(defn >
  ([m path]
   (> m path {}))
  ([m path config-state]
   (merge config-state
          {::🌿/stem (::🌿/stem m)
           ::🌿/path (or path [::🌿/local])})))

(defn +
  ([m suffix]
   (+ m suffix {}))
  ([m suffix config-state]
   (> m (into (::🌿/path m [::🌿/local]) (->v suffix)) config-state)))

#?(:clj (defn explain-trace [{:keys [stack]}]
          (->> stack
               (map-indexed
                (fn [i k]
                  (str (apply str (repeat (* 2 i) " "))
                       (if (zero? i) "" "└─ ")
                       k)))
               (str/join "\n"))))

(def ^:dynamic *trace* nil)

(defn trace-push! [f]
  (let [parent (some-> @*trace* :stack peek)]
    (swap! *trace*
           #(cond-> %
              parent
              (update :queries conj [parent f])
              (nil? parent)
              (update :roots conj f)
              :always
              (update :stack conj f)))))

(defn static-trace-push! [sym m]
  (let [deps (::🌿/deps m)]
    (cond-> *trace*
      deps
      (swap! update :static assoc sym (if (coll? deps) (into {} deps) #{deps})))))

(defn trace-pop! []
  (swap! *trace* update :stack pop))

(defn q [m f & opts]
  (when *trace* (trace-push! f))
  (try
    (apply f m opts)
    (finally
      (when *trace* (trace-pop!)))))

#?(:clj
   (defmacro trace [& forms]
     `(binding [*trace* (atom {})]
        ~@forms
        (deref *trace*))))

#?(:clj
   (defmacro trace-me [f & body]
     `(if-not *trace*
        (do ~@body)
        (do (trace-push! ~f)
            (try (do ~@body) (finally (trace-pop!)))))))

#?(:clj
   (defmacro defq
     {:clj-kondo/lint-as 'clojure.core/defn}
     [sym & decls]
     (let [[_doc-string decls] (if (string? (first decls))
                                 [(first decls) (next decls)]
                                 [nil decls])
           [attr-map decls]    (if (map? (first decls))
                                 [(first decls) (next decls)]
                                 [nil decls])
           attr-map            (if (and (list? (first decls))
                                        (map? (last decls)))
                                 (last decls)
                                 attr-map)
           impl                (symbol (str sym "--nextjournal--stem--impl"))
           k                   (str (ns-name *ns*) "/" sym)]
       `(do
          (defn ~sym ~@decls)
          #?(:clj  (var ~sym)
             :cljs ~sym)))))
