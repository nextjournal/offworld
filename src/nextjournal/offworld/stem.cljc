(ns nextjournal.offworld.stem
  (:refer-clojure :exclude [+])
  #?(:cljs
     (:require-macros
      [nextjournal.offworld.stem :refer [trace trace-me defq]]))
  (:require
   #?(:clj [clojure.string :as str]) 
   [nextjournal.offworld.stem :as-alias 🌿]
   [nextjournal.offworld :as-alias 🪐]
   [nexus.registry :as nxr]
   #_(:clj [nextjournal.offworld.util :as ou])))

(defn ->v [x] (if (sequential? x) (into [] x) [x]))

(defn id
  ([path] (id path []))
  ([path suffixes]
   #_(->> (->v path)
          (into suffixes)
          flatten
          (map name)
          (interpose "-")
          (apply str))))

#?(:cljs
   (nxr/register-placeholder! ::🌿/el
     (fn [_ path-or-id]
       (js/document.getElementById
        (cond
          (string? path-or-id)     path-or-id
          (sequential? path-or-id) (id path-or-id))))))

(defn init-state [state]
  (merge state {::🌿/stem state}))

(defn local [m]
  (let [stem (::🌿/stem m)
        path (::🌿/path m)]
    (get-in stem path)))

(defn +
  "This fn signature standardizes our convention for writing render-fns.
  - We pass a single map to a render-fn, i.e. replicant's \"state\".
  - We include a domain in the state."
  [m path & {:as config-state}]
  (let [stem (::🌿/stem m)]
    (merge config-state
           {::stem stem
            ::path (if (sequential? path)
                     (into [::local] (filterv #(not= % ::local) path))
                     [::local path])})))

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
