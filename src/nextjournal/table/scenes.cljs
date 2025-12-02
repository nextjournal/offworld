(ns nextjournal.table.scenes
  (:require [nextjournal.table.ui :as ui]
            [portfolio.replicant :refer-macros [defscene]]
            [replicant.dom :as r]
            [portfolio.ui :as portfolio]))

(defscene init-view []
  (ui/render {:number 0}))

(defn main []
  (portfolio/start!
   {:config
    {:css-paths ["/css/pico.min.css"]
     :viewport/defaults
     {:background/background-color "#fdeddd"}}})  )


(r/set-dispatch! #(prn :dispatch %1 %2))
