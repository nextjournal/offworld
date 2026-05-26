(ns nextjournal.offworld.demo
  #_ (:require
      [nextjournal.offworld.demo.load-builder :as lb]
      [nextjournal.offworld.demo.scan :as scan]))

(defn init-state [state]
  (-> state
      #_lb/init-state
      #_scan/init-state))
