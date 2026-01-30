(ns nextjournal.table.ui.holiday
  (:require
   [nexus.registry :as nxr]
   [nextjournal.baseline :as k]))

(def rand-holiday #(rand-nth [:gift-day :bird-day :egg-day :squash-day]))

(def icons {:gift-day   "🎁"
            :bird-day   "🦃"
            :egg-day    "🥚"
            :squash-day "🎃"})

(nxr/register-action! ::day
  (fn [_ ?]
    [[:effects/save [:long :path :to :holiday]
      (when ? (rand-holiday))]]))

(k/register! ::day
  (fn [ctx] (get-in ctx [:long :path :to :holiday])))

(k/register! ::icon
  (fn [ctx] (icons (k/read ctx ::day))))

(defn switch [state]
  [:input {:id     ::switch
           :type   :checkbox
           :switch true
           :value  (k/read state ::day)
           :on     {:change [[::day [:event.target/checked]]]}}])

(defn panel [state]
  [:div {:style {:position :fixed
                 :top      10
                 :right    10}}
   [:div.flex
    [:label {:for   ::switch
             :style {:user-select :none}} "Holiday Mode: "]
    (switch state)]
   (when-let [day (k/read state ::day)]
     [:div "It's " (name day) "."])])
