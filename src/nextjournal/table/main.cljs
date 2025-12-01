(ns nextjournal.table.main
  (:require [nexus.core :as nexus]
            [replicant.dom :as r]))


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

(def !state
  (atom {}))

(def nexus
  {:nexus/system->state deref
   :nexus/effects {:effects/save (fn save [_ store path value]
                                   (swap! store assoc-in path value))}
   :nexus/actions {:actions/inc (fn inc [state path]
                                  [[:effects/save path (+ (:step state) (get-in state path))]])}
   :nexus/placeholders {:event.target/value (fn event-target-value [{:replicant/keys [dom-event]}]
                                              (prn :event.target/value dom-event)
                                              (some-> dom-event .-target .-value))
                        :fmt/number (fn fmt-number [_ value]
                                      (prn :fmt/number value)
                                      (or (some-> value parse-long) 0))}})

(r/set-dispatch! #(nexus/dispatch nexus !state %1 %2))

(defonce root-el
  (js/document.getElementById "app"))

(add-watch !state ::render (fn [_ _ _ new-state]
                             (r/render root-el (render new-state))))

;; Trigger the initial render
(reset! !state {:number 0, :step 3})

