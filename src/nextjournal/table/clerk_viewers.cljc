(ns nextjournal.table.clerk-viewers
    (:require
     [nextjournal.clerk :as clerk]
     [replicant.string :as rstr]
     [nextjournal.table.ui :as ui]))

(def replicant-ssr
  {:transform-fn (clerk/update-val (comp clerk/html rstr/render))})
