(ns nextjournal.table.ui
  (:require
   [nextjournal.table.ui.omnibox :as omnibox-ui]
   [nextjournal.table.ui.nested-grid :as nested-grid-ui]
   [nextjournal.table.ui.holiday :as holiday]
   [nextjournal.baseline :as k]))

(defn render [state]
  (let [state+ (k/+ state)]
    [:main {:id "app"}
     [:div.flex
      (for [col [[:transport/destination :address/city]
                 [:transport/destination :address/postcode]]]
        (omnibox-ui/omnibox
         (k/sub state+ [:omnibox col])))]
     (nested-grid-ui/nested-grid
      (k/sub state+ [:grid]))
     (holiday/panel state+)]))
