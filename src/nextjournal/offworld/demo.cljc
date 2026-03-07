(ns nextjournal.offworld.demo
  (:require
   [nextjournal.ductile.load-builder :as lb]
   [nextjournal.offworld.demo.scan :as scan]))

(defn init-state [state]
  (-> state
      lb/init-state
      scan/init-state))
