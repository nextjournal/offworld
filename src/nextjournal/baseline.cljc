(ns nextjournal.baseline
  (:refer-clojure :exclude [+])
  #?(:cljs
     (:require-macros
      [nextjournal.baseline :refer [trace trace-me defq]]))
  (:require
   [clojure.string :as str]
   [nextjournal.baseline :as-alias k]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]))

(defn init-state [state]
  (merge state {::k/stem state}))

(defn local [{::k/keys [stem path]}]
  (get-in stem path))



(defn +
  "This fn signature standardizes our convention for writing render-fns.
  - We pass a single map to a render-fn, i.e. replicant's \"state\".
  - We include a domain in the state."
  [{::k/keys [stem]} path & {:as config-state}]
  (merge config-state
         {::stem stem
          ::path (if (sequential? path)
                   (into [::local] (remove #{::local}) path)
                   [::local path])}))

(defn explain-trace [{:keys [stack]}]
  (->> stack
       (map-indexed
        (fn [i k]
          (str (apply str (repeat (* 2 i) " "))
               (if (zero? i) "" "└─ ")
               k)))
       (str/join "\n")))

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
     [sym args & body]
     `(defn ~sym ~args
        (if-not *trace*
          (do ~@body)
          (do (trace-push! '~sym)
              (try (do ~@body) (finally (trace-pop!))))))))

(defn get-b [stem] (:b stem))
(defn get-a [stem] (when (q stem get-b) (:a stem)))
(defn render-a [stem] (q stem get-a))

(defn get-y [stem] (trace-me `get-y (:y stem)))
(defn get-x [stem] (trace-me `get-x (when (get-y stem) (:x stem))))
(defn render-x [stem] (get-x stem))

(defq get-i [stem] (:i stem))
(defq get-j [stem] (when (get-i stem) (:j stem)))
(defn render-j [stem] (get-j stem))

(comment
  (trace (q {:a 1} get-a))
  (trace (render-a {:a 1}))

  (trace (get-x {:x 1}))
  (trace (render-x {:x 1}))

  (trace (get-j {:j 1}))
  (trace (render-j {:j 1})))
