(ns nextjournal.baseline
  (:refer-clojure :exclude [+])
  (:require 
   [nextjournal.baseline :as-alias k]))

(def conjv (fnil conj []))

(def registry (atom {}))

(defn register! [k f]
  (swap! registry assoc k f))

(defn with-db
  ([m] (with-db m m))
  ([m db]
   (assoc m ::k/db db)))

(def + with-db)

(defn q
  ([{::k/keys [db]} k]
   ((@registry k) (with-db db)))
  ([{::k/keys [db]} k & opts]
   (apply (@registry k) (with-db db) opts)))

(defn append-path-prefix [state state-old path]
  (assoc state ::k/path-prefix
         (vec (concat (::k/path-prefix state-old) path))))

(defn sub [state path]
  (-> state
      (get-in path)
      (append-path-prefix state path)
      (with-db (::k/db state))))
