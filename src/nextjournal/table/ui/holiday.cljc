(ns nextjournal.table.ui.holiday
  (:require
   [nexus.registry :as nxr]
   [nextjournal.baseline :as k]))

(def day->icon
  {:gift-day   "🎁"
   :egg-day    "🥚"
   :bird-day   "🦃"
   :squash-day "🎃"})

(def season->holiday
  {:spring :egg-day
   :summer :bird-day
   :fall   :squash-day
   :winter :gift-day})

(k/register! ::holiday-mode?
  (fn [db] (get-in db [::path :to :holiday-mode?])))

(nxr/register-action! ::begin
  #(do [[:effects/save [::path :to :holiday-mode?] true]]))

(nxr/register-action! ::cancel
  #(do [[:effects/save [::path :to :holiday-mode?] false]]))

(nxr/register-action! ::toggle
  (fn [_ ?] [(if ? [::begin] [::cancel])]))

(k/register! ::season
  (fn [db] (get-in db [::path :to :season] :spring)))

(nxr/register-action! ::season
  (fn [_ s] [[:effects/save [::path :to :season] (keyword s)]]))

(k/register! ::day
  (fn [db] (season->holiday (k/q db ::season))))

(k/register! ::icon
  (fn [db] (when (k/q db ::holiday-mode?)
             (day->icon (k/q db ::day)))))

(defn switch [state]
  [:input {:id     ::mode-switch
           :type   :checkbox
           :switch true
           :value  (k/q state ::day)
           :on     {:change [[::toggle [:event.target/checked]]]}}])

(defn select [state]
  [:select {:id ::season-select
            :on {:change [[::season [:event.target/value]]]}}
   [:option {:value :spring} "Spring"]
   [:option {:value :summer} "Summer"]
   [:option {:value :fall} "Fall"]
   [:option {:value :winter} "Winter"]])

(defn panel [state]
  [:div.fixed.top-2.right-2
   [:div.flex
    [:label.select-none {:for ::season-select}
     "Season: "]
    (select state)]
   [:div.flex
    [:label.select-none {:for ::mode-switch}
     "Holiday Mode: "]
    (switch state)]
   (when-let [day (k/q state ::day)]
     [:div "It's " (name day) "."])])
