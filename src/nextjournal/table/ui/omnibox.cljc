(ns nextjournal.table.ui.omnibox
  (:require [clojure.string :as str]
            [nextjournal.table.ui.utils :as utils]
            [nextjournal.table.filters :as filters]
            [portfolio.replicant :refer [defscene]]))

(def icon-filter
  [:svg {:viewBox "0 0 20 20"
         :fill "currentColor"
         :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M18 1H2V3L10 13L18 3V1Z"}]
   [:path {:d "M8 5H12V18L8 15V5Z"}]])

(defn remove-filter-button [opts]
  [:button
   {:class ["px-[6px]" "py-[3px]" "rounded-l-[3px]" "text-slate-400" "hover:text-inherit"]
    :on {:click [[::remove-filter]]}}
   "×"])

#?(:cljs (defscene remove-filter-scene []
           (remove-filter-button {})))

(defn filter-button [{:keys [label]}]
  [:div
   {:class ["ring-1" "ring-slate-300" "bg-white" "text-[12px]" "px-[6px]" "py-[3px]" "rounded-[3px]" "group"]}
   label])

#?(:cljs (defscene filter-button-scene []
           (filter-button {:value ""})))

(defn input [opts]
  [:input
   {:type "text"
    :class ["w-full" "cursor-default" "rounded-[3px]" "px-[6px]"
            "ring-1" "ring-slate-300" "font-normal" "placeholder-slate-400"
            "focus:outline-none" "focus:ring-2" "focus:ring-blue-500" "sm:leading-6"
            "bg-white"]
    :placeholder "Filter..."
    :data-testid (:data-testid opts)
    :value (:value opts)
    :on {:focus [[:effects/save (utils/conjv (:state/path-prefix opts) :focus?) true]]
         :blur [[:effects/save (utils/conjv (:state/path-prefix opts) :focus?) false]]
         :input [[:effects/save (utils/conjv (:state/path-prefix opts) :value) [:event.target/value]]
                 #_(do
                     (reset! !text (-> % .-currentTarget .-value))
                     (reset! !selected nil)
                     (compare-and-set! !expanded false true))]
         :keydown [[::keydown-client [:event/key]]
                   [::keydown (:state/path-prefix opts) [:event/key]]
                    #_(fn [e]
                        (case (.-key e)
                          "Enter"
                          (if-some [selected @!selected]
                            (on-row-click e (nth rows selected) opts)
                            (when-some [filter-from-text (or ; don't put text->filter in cond :else, always fall back to text filter when ->numeric returns nil (eg for filtering numeric by "2*")
                                                          (cond
                                                            (:text->filter opts) ((:text->filter opts) @!text)
                                                            numeric? (text->numeric-filter @!text)
                                                            date? (text->date-filter @!text)
                                                            keyword? (text->filter (name @!text)))
                                                          (text->filter @!text))]
                              (.preventDefault e)
                              (set-filters (conj filters filter-from-text))
                              (reset! !text "")
                              (reset! !expanded false)))

                          "Escape"
                          (do
                            (.preventDefault e)
                            (reset! !selected nil)
                            (reset! !text "")
                            (reset! !expanded false))

                          " "
                          (when-some [selected @!selected]
                            (on-row-click e (nth rows selected) opts))

                          "ArrowDown"
                          (do
                            (.preventDefault e)
                            (update-selected opts 1))

                          "ArrowUp"
                          (do
                            (.preventDefault e)
                            (update-selected opts -1))

                          nil))]}}])

#?(:cljs (defscene input-scene []
           (input {})))

(defn popover [opts]
  [:div
   (when-let [filter-to-add (:filter-to-add opts)]
     [:li.flex
      [:span.flex.mt-1 [:div.w-4.h-4.text-slate-400 icon-filter]]
      (:label filter-to-add)])])

(defn build-state [state]
  (let [{:keys [focus? value]} (::input state)]
    (cond-> (assoc state :popover-visible? (boolean focus?))
      (not (str/blank? value))
      (assoc :filter-to-add (filters/text->filter value)))))

(defn omnibox [opts]
  (let [opts' (build-state opts)]
    [:div
     (input (utils/substate opts' [::input]))
     (when (:popover-visible? opts')
       (popover opts'))
     (pr-str opts')]))

