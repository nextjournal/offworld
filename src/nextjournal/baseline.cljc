(ns nextjournal.baseline
  (:refer-clojure :exclude [+])
  (:require 
   [nextjournal.baseline :as-alias k]))

(def conjv (fnil conj []))

(def registry (atom {}))

(defn register! [k f]
  (swap! registry assoc k f))

(defn with-ctx
  ([m] (with-ctx m m))
  ([m ctx]
   (assoc m ::k/ctx ctx)))

(def + with-ctx)

(defn read
  ([{::k/keys [ctx]} k]
   ((@registry k) (with-ctx ctx)))
  ([{::k/keys [ctx]} k & opts]
   (apply (@registry k) (with-ctx ctx) opts)))

(defn append-path-prefix [state state-old path]
  (assoc state ::k/path-prefix
         (vec (concat (::k/path-prefix state-old) path))))

(defn sub [state path]
  (-> state
      (get-in path)
      (append-path-prefix state path)
      (with-ctx (::k/ctx state))))
