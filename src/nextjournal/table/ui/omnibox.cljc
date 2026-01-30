(ns nextjournal.table.ui.omnibox
  (:require [clojure.string :as str]
            [nextjournal.table.util :as u]
            [nextjournal.table.ui.utils :as utils]
            [nextjournal.table.filters :as filters]
            [portfolio.replicant :refer [defscene]]))

(def icon-filter
  [:svg {:viewBox "0 0 20 20"
         :fill    "currentColor"
         :xmlns   "http://www.w3.org/2000/svg"}
   [:path {:d "M18 1H2V3L10 13L18 3V1Z"}]
   [:path {:d "M8 5H12V18L8 15V5Z"}]])

(defn remove-filter-button [_]
  [:button
   {:class ["px-[6px]" "py-[3px]" "rounded-l-[3px]" "text-slate-400" "hover:text-inherit"]
    :on    {:click [[::remove-filter]]}}
   "×"])

#?(:cljs (defscene remove-filter-scene []
           (remove-filter-button {})))

(defn filter-button [{:keys [label]}]
  [:div
   {:class ["ring-1" "ring-slate-300" "bg-white" "text-[12px]" "px-[6px]" "py-[3px]" "rounded-[3px]" "group"]}
   label])

#?(:cljs (defscene filter-button-scene []
           (filter-button {:value ""})))

(defn id [state & suffixes]
  (->> (concat (:state/path-prefix state) suffixes)
       flatten
       (map name)
       (interpose "-")
       (apply str)))

(defn choice-id [{:keys [parent-id index]}]
  (str parent-id index))

(defn input [{:as state :keys [input-id popover-id anchor-name]}]
  [:input
   {:id          input-id
    :type        "text"
    :class       ["w-full" "cursor-default" "rounded-[3px]" "px-[6px]"
                  "ring-1" "ring-slate-300" "font-normal" "placeholder-slate-400"
                  "focus:outline-none" "focus:ring-2" "focus:ring-blue-500" "sm:leading-6"
                  "bg-white"]
    :style       {:anchor-name anchor-name}
    :placeholder "Filter..."
    :value       (:value state)
    :on          {:focus   [[:dom-node/show-popover {:node [:document/element-by-id popover-id]}]]
                  :input   [[:effects/save
                             (utils/conjv (:state/path-prefix state) :value)
                             [:event.target/value]]]
                  :keydown [[::keydown-input-client
                             {:key           [:event/key]
                              :key-modifiers [:event/key-modifiers]
                              :popover-id    popover-id
                              :child-id      (choice-id {:parent-id popover-id :index 0})}]
                            [::keydown-input
                             {:key           [:event/key]
                              :key-modifiers [:event/key-modifiers]
                              :path          (:state/path-prefix state)}]
                            #_(fn [e]
                                (case (.-key e)
                                  "Enter"
                                  (if-some [selected @!selected]
                                    (on-row-click e (nth rows selected) state)
                                    (when-some [filter-from-text (or
                                                                  (cond
                                                                    (:text->filter state) ((:text->filter state) @!text)
                                                                    numeric?              (text->numeric-filter @!text)
                                                                    date?                 (text->date-filter @!text)
                                                                    keyword?              (text->filter (name @!text)))
                                                                  (text->filter @!text))]
                                      (.preventDefault e)
                                      (set-filters (conj filters filter-from-text))
                                      (reset! !text "")
                                      (reset! !expanded false)))
                                  nil))]}}])

#?(:cljs (defscene input-scene []
           (input {})))

(defn popover [{:as   state
                :keys [model choices filters-to-add anchor-name input-id popover-id filters]}]
  (let [child-indices (vec (range (count (concat filters-to-add choices))))]
    [:div.w-full.p-1
     {:id      popover-id
      :popover :manual
      :style   {:background      :white
                :position        :fixed
                :position-anchor anchor-name
                :position-area   "bottom center"
                :max-height      212
                :overflow        :auto}}
     (for [i    (range (count filters-to-add))
           :let [{:keys [label]} (nth filters-to-add i)
                 id      (choice-id {:parent-id popover-id :index i})
                 next-id (some-> child-indices (get (inc i)) (#(str popover-id %)))
                 prev-id (some-> child-indices (get (dec i)) (#(str popover-id %)))
                 add     [::add-filter (:state/path-prefix state)
                          (first filters-to-add)]]]
       [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400
        {:on {:click [add]}}
        [:span.flex.mt-1.focus:outline-none
         {:id       id
          :tabindex 0
          :on       {:keydown [[::keydown-choice-item-client
                                {:key        [:event/key]
                                 :input-id   input-id
                                 :popover-id popover-id
                                 :next-id    next-id
                                 :prev-id    prev-id}]
                               [::keydown-choice-item
                                {:key      [:event/key]
                                 :on-enter [add]}]]}}
         [:div.w-4.h-4.text-slate-400 icon-filter]]
        label])
     (for [ci   (range (count choices))
           :let [choice  (nth (vec choices) ci)
                 i       (+ ci (count filters-to-add))
                 id      (choice-id {:parent-id popover-id :index i})
                 next-id (some-> child-indices (get (inc i)) (#(str popover-id %)))
                 prev-id (some-> child-indices (get (dec i)) (#(str popover-id %)))
                 filter  (filters/build-equals-filter choice)
                 value   (contains? (set filters) filter)]]
       [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400
        {:on {:change  [(if value
                          [::remove-filter (:state/path-prefix state) filter]
                          [::add-filter (:state/path-prefix state) filter])]
              :value   value
              :keydown [[::keydown-choice-item-client
                         {:key        [:event/key]
                          :input-id   input-id
                          :popover-id popover-id
                          :next-id    next-id
                          :prev-id    prev-id}]]}}
        [:input.focus:outline-none
         {:type :checkbox
          :id   id}]
        [:label {:for   id
                 :style {:user-select :none}}
         choice]])]))

(defn filter-pill [state {:keys [label] :as filter}]
   [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400.text-xs
    [:span.flex.mt-1.focus:outline-none
     [:div.w-3.h-3.text-slate-400 icon-filter]]
    label
    [:span {:style {:cursor      :pointer
                    :margin-left 5}
            :on    {:click [[::remove-filter (:state/path-prefix state) filter]]}}
     "X"]])

(defn omnibox [{:keys [value filters] :as state}]
  (let [state' (merge state
                      {:popover-id  (id state :popover)
                       :input-id    (id state :input)
                       :anchor-name (apply str "--" (id state))}
                      (when-not (str/blank? value)
                        {:filters-to-add [(filters/text->filter value)]}))]
    [:div
     (input state')
     (popover state')
     (map filter-pill (repeat state') filters)]))
