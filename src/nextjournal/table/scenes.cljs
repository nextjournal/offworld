(ns nextjournal.table.scenes
  (:require [nextjournal.table.ui :as ui]
            [nextjournal.table.ui.omnibox :as omnibox-ui]
            [portfolio.replicant :refer-macros [defscene]]
            [replicant.dom :as r]
            [portfolio.ui :as portfolio]))

(defscene init-view []
  (ui/render {:number 0}))

(defscene anchor []
  (omnibox-ui/anchor {}))

(defscene filter-button []
  (omnibox-ui/filter-button {:value "s"}))

(defscene remove-filter-2 []
  (omnibox-ui/remove-filter-button {}))

(defn main []
  (portfolio/start!
   {:config
    {:css-paths ["/css/ductile.css"]
     :viewport/defaults
     {:background/background-color "#fdeddd"}}})  )


(r/set-dispatch! #(prn :dispatch %1 %2))
