(ns nextjournal.table.ui
  (:require [nextjournal.table.ui.omnibox :as omnibox-ui]
            [nextjournal.table.ui.utils :as utils]))

(defn render [state]
  [:div
   [:pre (pr-str state)]
   (for [col [:address/city :address/postcode]]
     (omnibox-ui/omnibox (utils/substate state [col])))])
