(ns nextjournal.offworld.demo.ui.omnibox
  (:require
   [clojure.string :as str]
   [nexus.registry :as nxr]
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.demo.filters :as filters]
   [nextjournal.offworld.demo.ui.holiday :as 🎄]
   [nextjournal.offworld.demo.ui.omnibox :as-alias ob]
   #?(:cljs [portfolio.replicant :refer [defscene]])))

(nxr/register-action! ::ob/keydown-input ^::🪐/client
  (fn [_ {:keys [key popover-id choice-id anchor-id filters-to-add path]
          mods  :key-modifiers}]
    (cond
      (= key "Escape")    [[:event/prevent-default]
                           [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                           [:input/clear {:node [:document/element-by-id popover-id]}]
                           [:effects/save (conj path :value) ""]]
      (= key "ArrowDown") [[:event/prevent-default]
                           (when choice-id
                             [:dom-node/focus {:node [:document/element-by-id choice-id]}])]
      (= key "Enter")     [[:event/prevent-default]
                           [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                           [:input/clear {:node [:document/element-by-id anchor-id]}]
                           [:effects/save (conj path :value) ""]
                           [:effects/conj (conj path :filters) (first filters-to-add) #{}]]
      (and (mods :shift)
           (= key "Tab")) [[:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]]
      :else               nil)))

(nxr/register-action! ::ob/keydown-choice-item ^::🪐/client
  (fn [_ {:keys [key popover-id prev-id next-id choice-id anchor-id filters-to-add path]}]
    (case key
      "Escape"    [[:event/prevent-default]
                   [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                   [:dom-node/blur]]
      "Enter"     [[:event/prevent-default]
                   [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                   [:dom-node/blur {:node [:document/element-by-id anchor-id]}]
                   [:dom-node/set-checked {:node  [:document/element-by-id choice-id]
                                           :value true}]
                   [:effects/conj (conj path :filters) (first filters-to-add) #{}]]
      "ArrowUp"   [[:event/prevent-default]
                   (if prev-id
                     [:dom-node/focus {:node [:document/element-by-id prev-id]}]
                     [:dom-node/focus {:node [:document/element-by-id anchor-id]}])]
      "ArrowDown" [[:event/prevent-default]
                   (when next-id
                     [:dom-node/focus {:node [:document/element-by-id next-id]}])]
      "Tab"       [(when-not next-id
                     [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}])]
      nil)))

(nxr/register-action! ::ob/add-filter ^::🪐/server
  (fn [state path value]
    (let [p           (conj path :filters)
          old-filters (get-in state p #{})
          new-filters (conj old-filters value)]
      [[:effects/save p new-filters]])))

(nxr/register-action! ::ob/remove-filter ^::🪐/server
  (fn [state path value]
    (let [p           (conj path :filters)
          old-filters (get-in state p #{})
          new-filters (disj old-filters value)]
      [[:effects/save (conj path :filters) new-filters]])))

(nxr/register-action! ::ob/toggle-choice ^::🪐/server
  (fn [state {:keys [path k value]}]
    (let [old-set (get-in state path #{})
          new-set (if value
                    (disj old-set k)
                    (conj old-set k))]
      [[:effects/save path new-set]])))

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

(defn id [path & suffixes]
  (->> (concat path suffixes)
       flatten
       (map name)
       (interpose "-")
       (apply str)))

(defn choice-id [parent-id index]
  (str parent-id index))

(defn anchor [{:keys    [anchor-id popover-id filters-to-add]
               ::k/keys [stem path]}]
  [:input
   {:id          anchor-id
    :type        "text"
    :class       ["w-full" "cursor-default" "rounded-[3px]" "px-[6px]"
                  "ring-1" "ring-slate-300" "font-normal" "placeholder-slate-400"
                  "focus:outline-none" "focus:ring-2" "focus:ring-blue-500" "sm:leading-6"
                  "bg-white"]
    :style       {:anchor-name (str "--" anchor-id)}
    :placeholder (str "Filter..." (🎄/get-icon stem))
    :on          {:focus   [[:dom-node/show-popover
                             {:node [:document/element-by-id popover-id]}]]
                  :input   [[:effects/save (conj path :value) [:event.target/value]]]
                  :keydown [[::keydown-input
                             {:choice-id      (choice-id popover-id 0)
                              :filters-to-add filters-to-add
                              :key            [:event/key]
                              :key-modifiers  [:event/key-modifiers]
                              :path           path
                              :anchor-id      anchor-id
                              :popover-id     popover-id}]]}}])

#?(:cljs (defscene anchor-scene []
           (anchor {})))

(defn popover [{:keys    [choices filters-to-add filters anchor-id popover-id]
                ::k/keys [path]}]
  (let [child-indices (vec (range (count (concat filters-to-add choices))))]
    [:div.w-full.p-1
     {:id      popover-id
      :popover :manual
      :style   {:background      :white
                :position        :fixed
                :position-anchor (str "--" anchor-id)
                :position-area   "bottom center"
                :max-height      212
                :overflow        :auto}}
     (for [i    (range (count filters-to-add))
           :let [{:keys [label]} (nth filters-to-add i)
                 id      (choice-id  popover-id i)
                 next-id (some-> child-indices (get (inc i)) (#(str popover-id %)))
                 prev-id (some-> child-indices (get (dec i)) (#(str popover-id %)))]]
       [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400
        {:on {:click [[::add-filter path (first filters-to-add)]]}}
        [:span.flex.mt-1.focus:outline-none
         {:id       id
          :tabindex 0
          :on       {:keydown [[::keydown-choice-item
                                {:anchor-id      anchor-id
                                 :choice-id      id
                                 :filters-to-add filters-to-add
                                 :key            [:event/key]
                                 :next-id        next-id
                                 :path           path
                                 :popover-id     popover-id
                                 :prev-id        prev-id}]]}}
         [:div.w-4.h-4.text-slate-400 icon-filter]]
        label])
     (for [ci   (range (count choices))
           :let [choice  (nth (vec choices) ci)
                 i       (+ ci (count filters-to-add))
                 id      (choice-id popover-id i)
                 next-id (some-> child-indices (get (inc i)) (#(str popover-id %)))
                 prev-id (some-> child-indices (get (dec i)) (#(str popover-id %)))
                 filter  (filters/build-equals-filter choice)]]
       [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400
        {:on {:change  [(if (contains? filters filter)
                          [::remove-filter path filter]
                          [::add-filter path filter])]
              :keydown [[::keydown-choice-item
                         {:anchor-id      anchor-id
                          :choice-id      id
                          :filters-to-add [filter]
                          :key            [:event/key]
                          :next-id        next-id
                          :path           path
                          :popover-id     popover-id
                          :prev-id        prev-id}]]}}
        [:input.focus:outline-none
         {:type :checkbox
          :id   id}]
        [:label {:for   id
                 :style {:user-select :none}}
         choice]])]))

(defn filter-pill [{{:keys [label] :as this-filter}
                    :filter ::k/keys [path]}]
  [:li.flex.ps-1.rounded-sm.focus-within:outline-4.outline-red-400.text-xs
   [:span.flex.mt-1.focus:outline-none
    [:div.w-3.h-3.text-slate-400 icon-filter]]
   label
   [:span {:style {:cursor      :pointer
                   :margin-left 5}
           :on    {:click [[::remove-filter path this-filter]]}}
    "X"]])

(defn omnibox [{:as      state
                :keys    [choices]
                ::k/keys [stem path]}]
  (let [{:keys
         [filters value]} (get-in stem path)
        config            (merge
                           {:choices    choices
                            :filters    filters
                            :popover-id (id path :popover)
                            :anchor-id  (id path :anchor)}
                           (when-not (str/blank? value)
                             {:filters-to-add
                              [(filters/text->filter value)]}))]
    [:div
     (anchor (k/+ state path config))
     (popover (k/+ state path config))
     (->> filters
          (map #(do {:filter %}))
          (map k/+ (repeat state) (repeat path))
          (map filter-pill))]))
