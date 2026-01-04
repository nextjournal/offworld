(ns nextjournal.table.ui
  (:require [nextjournal.table.ui.omnibox :as omnibox-ui]
            [nextjournal.table.ui.utils :as utils]))

(defn render [state]
  [:div
   [:div {:style {:max-height 300
                  :max-width  400
                  :overflow   :auto}}
    [:div {:data-on:click "alert('hi')"
           :style {:height           500
                   :width            500
                   :background-color :red}}]]
   [:pre (pr-str state)]
   (for [col [:address/city :address/postcode]]
     (omnibox-ui/omnibox (utils/substate state [col])))])
