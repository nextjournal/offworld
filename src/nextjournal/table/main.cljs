(ns nextjournal.table.main
  (:require [nexus.registry :as nxr]
            [replicant.dom :as r]))


(defn save [_ store path value]
  (swap! store assoc-in path value))

(defn increment [state path]
  [[:effects/save path (+ (:step state) (get-in state path))]])

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

;; App state
(def store (atom {}))

;; Handle user input: register effects, actions and placeholders.
;; If you don't like registering these globally, the next section
;; shows how to use nexus.core, which has no implicit state.
(nxr/register-effect! :effects/save save)
(nxr/register-action! :actions/inc increment)

(nxr/register-placeholder!
 :event.target/value
 (fn [{:replicant/keys [dom-event]}]
   (some-> dom-event .-target .-value)))

(nxr/register-placeholder!
 :fmt/number
 (fn [_ value]
   (or (some-> value parse-long) 0)))

(nxr/register-system->state! deref)

;; Wire up the render loop
(r/set-dispatch! #(nxr/dispatch store %1 %2))
(add-watch store ::render #(r/render js/document.body (render %4)))

;; Trigger the initial render
(reset! store {:number 0, :step 1})

