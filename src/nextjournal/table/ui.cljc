(ns nextjournal.table.ui)

(defn render [state]
  [:div
   [:p "Number: " (:number state)]
   [:div
    [:label "Step size: "]
    [:input
     {:value (:step state)
      :on
      {:input
       [[:effects/save [:step] [:fmt/number [:event.target/value]]]]}}]]
   [:button.btn
    {:on {:click [[:actions/inc [:number]]]}}
    "Count!"]])
