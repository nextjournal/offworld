(ns nextjournal.offworld.demo.ui.holiday
  (:require
   [nexus.registry :as nxr]
   [nextjournal.baseline :as k :refer [defq]]
   [nextjournal.offworld :as-alias ow]))

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

(defq get-holiday-mode? [stem]
  (get-in stem [::path :to :holiday-mode?]))

(nxr/register-action! ::toggle
  (fn [_ ?] [[:effects/save [::path :to :holiday-mode?] ?]]))

(defq get-season [stem]
  (get-in stem [::path :to :season] :spring))

(nxr/register-action! ::season
  (fn [_ s] [[:effects/save [::path :to :season] (keyword s)]]))

(defq get-day {::k/deps #{`get-season}} [stem]
  (season->holiday (get-season stem)))

(nxr/register-action! ::randomize ^::ow/client
  (fn [_ key-mods season]
    (let [path        [::path :to :season]
          reset?      (contains? (set key-mods) :shift)
          rand-season (first (rand-nth (seq (dissoc season->holiday season))))]
      (if reset?
        [[:browser/alert "Holiday season has been reset."]
         [:effects/save path :spring]]
        [[:effects/save path rand-season]]))))

(defq get-icon {::k/deps #{`get-holiday-mode? `get-day}} [stem]
  (when (get-holiday-mode? stem)
    (day->icon (get-day stem))))

(defn switch [state]
  [:input {:id     ::mode-switch
           :type   :checkbox
           :switch true
           :value  (get-day state)
           :on     {:change [[::toggle [:event.target/checked]]]}}])

(defn select [state]
  [:select {:id    ::season-select
            :value (get-season state)
            :on    {:change [[::season [:event.target/value]]]}}
   [:option {:value :spring} "Spring"]
   [:option {:value :summer} "Summer"]
   [:option {:value :fall} "Fall"]
   [:option {:value :winter} "Winter"]])

(defn randomize-button [{::k/keys [stem]}]
  [:button {:on {:click [[::randomize
                          [:event/key-modifiers]
                          (get-season stem)]]}}
   "Randomize (shift-click to reset)"])

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
   (when-let [day (get-day state)]
     [:div "It's " (name day) "."])
   (randomize-button state)])

(comment
  (get-season {})
  (get-icon {::path {:to {:holiday-mode? true}}})
  (k/trace (get-icon {::path {:to {:holiday-mode? true}}})))
