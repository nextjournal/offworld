(ns nextjournal.table.ui
  (:require
   [nextjournal.table.ui.omnibox :as omnibox-ui]
   [nextjournal.table.ui.utils :as utils]))

(defn render [state]
  [:main {:id "app"}
   (for [col [:address/city :address/postcode]]
     (omnibox-ui/omnibox (utils/substate state [col])))])
