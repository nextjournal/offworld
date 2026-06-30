(ns nextjournal.offworld.stem
  (:refer-clojure :exclude [+ >])
  #?(:cljs
     (:require-macros
      [nextjournal.offworld.stem :refer [trace trace-me defq]]))
  (:require
   #?(:clj [clojure.string :as str])
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.stem :as-alias 🌿]
   [nextjournal.offworld.util :as ou]
   [nexus.registry :as nxr]))

(defn ->v [x] (if (sequential? x) (into [] x) [x]))

(defn id
  ([path] (id path []))
  ([path suffixes] (ou/encode (into (->v path) suffixes))))

(nxr/register-placeholder! ::🌿/el ^::🪐/client
  (fn [_ path-or-id]
    #?(:cljs
       (js/document.getElementById
        (cond
          (string? path-or-id)     path-or-id
          (sequential? path-or-id) (id path-or-id))))))

(defn init-state [state]
  (merge state {::🌿/stem state}))

(defn stem [state] (::🌿/stem state))

(defn path
  ([state] (path state []))
  ([state suffix] (into (::🌿/path state [::🌿/local])
                        (->v suffix))))

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
