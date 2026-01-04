(ns nextjournal.table.ui.nested-grid)

(defn nested-grid [state]
  [:div {:on    {:click [[:change-color :a]]}
         :style {:max-height 300
                 :max-width  400
                 :overflow   :auto}}
   [:div {:style {:height           500
                  :width            500
                  :color            :white
                  :padding          "1rem"
                  :background-color (:color state)}}
    "Click Me"]])
