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
  #(get-in % [::path :to :holiday-mode?]))

(nxr/register-action! ::toggle
  (fn [_ ?] [[:effects/save [::k/domain ::path :to :holiday-mode?] ?]]))

(k/register! ::season
  #(get-in % [::path :to :season] :spring))

(nxr/register-action! ::season
  (fn [_ s] [[:effects/save [::k/domain ::path :to :season] (keyword s)]]))

(k/register! ::day
  ^{::k/deps #{::season}}
  #(season->holiday (k/q % ::season)))

(k/register! ::icon
  ^{::k/deps #{::day ::holiday-mode?}}
  #(when (k/q % ::holiday-mode?)
     (day->icon (k/q % ::day))))

(k/register! ::icon-error
  ^{::k/deps #{::day ::holiday-mode?}}
  #(when (k/q % ::holiday-mode?)
     (day->icon (k/q % ::day-error))))

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

(comment
  (k/q {} ::season)
  (k/q {::k/domain {::path {:to {:holiday-mode? true}}}} ::icon)
  (k/trace {::k/domain {::path {:to {:holiday-mode? true}}}} ::icon)
  (k/trace {::path {:to {:holiday-mode? true}}} ::icon)
  (k/trace {::path {:to {:holiday-mode? true}}} ::icon-error))
