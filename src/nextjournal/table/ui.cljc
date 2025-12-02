(ns nextjournal.table.ui
  (:require [nextjournal.table.ui.omnibox :as omnibox-ui]))

(defn render [state]
  [:div
   [:pre (pr-str state)]
   (omnibox-ui/input state)])
