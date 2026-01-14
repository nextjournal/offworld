(ns nextjournal.table.ui
  (:require
   [nextjournal.table.ui.omnibox :as omnibox-ui]
   [nextjournal.table.ui.nested-grid :as nested-grid-ui]
   [nextjournal.table.ui.utils :as utils]))

(defn render [state]
  [:main {:id "app"}
   (for [col [:address/city :address/postcode]]
     (omnibox-ui/omnibox (utils/substate state [col])))
   (nested-grid-ui/nested-grid
    (utils/substate state [:grid]))])
