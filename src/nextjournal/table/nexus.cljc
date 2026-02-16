(ns nextjournal.table.nexus
  (:require
   [nexus.registry :as nxr]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.table.ui.omnibox :as-alias ob]
   [nextjournal.offworld :as-alias 🪐]))

(def nexus
  {:nexus/system->state deref
   :nexus/effects       {:effects/save
                         ^:nexus/batch
                         (fn [_ system path-vs]
                           (swap! system
                                  (fn [state]
                                    (reduce (fn [acc [path v]]
                                              (assoc-in acc path v))
                                            state path-vs))))
                         :event/prevent-default
                         ^::🪐/client (fn [{{:replicant/keys [dom-event]} :dispatch-data}]
                                        (.preventDefault dom-event))
                         :dom-node/focus
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs
                            (fn [{{:replicant/keys [node]} :dispatch-data} _ & {target-node :node}]
                              (.focus (or target-node node))))
                         :dom-node/blur
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs
                            (fn [{{:replicant/keys [node]} :dispatch-data} _ & {target-node :node}]
                              (.blur (or target-node node))))
                         :dom-node/set-checked
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs
                            (fn [{{:replicant/keys [node]} :dispatch-data} _ & {target-node :node :keys [value]}]
                              (set! (.-checked (or target-node node)) value)))
                         :dom-node/show-popover
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs (fn [{{:replicant/keys [node]} :dispatch-data} _ & {target-node :node}]
                                    (.showPopover (or target-node node))))
                         :dom-node/hide-popover
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs (fn [{{:replicant/keys [node]} :dispatch-data} _ & {target-node :node}]
                                    (.hidePopover (or target-node node))))
                         :browser/alert
                         ^::🪐/client
                         #?(:clj (fn [])
                            :cljs (fn [_ _ s] (js/alert s)))}
   :nexus/actions       {:actions/inc       (fn [state path]
                                              [[:effects/save path (+ (:step state) (get-in state path))]])
                         ::ng/scroll        (fn [_ path top left]
                                              [[:effects/save (concat path [:scroll-top]) top]
                                               [:effects/save (concat path [:scroll-left]) left]])
                         ::ng/resize        (fn [_ width height]
                                              [[:effects/save [:grid :width] width]
                                               [:effects/save [:grid :height] height]])
                         ::ob/add-filter    (fn [state path value]
                                              (let [p           (conj path :filters)
                                                    old-filters (get-in state p #{})
                                                    new-filters (conj old-filters value)]
                                                [[:effects/save (conj path :filters) new-filters]]))
                         ::ob/remove-filter (fn [state path value]
                                              (let [p           (conj path :filters)
                                                    old-filters (get-in state p #{})
                                                    new-filters (disj old-filters value)]
                                                [[:effects/save (conj path :filters) new-filters]]))
                         ::ob/toggle-choice
                         (fn [state {:keys [path k value]}]
                           (let [old-set (get-in state path #{})
                                 new-set (if value
                                           (disj old-set k)
                                           (conj old-set k))]
                             [[:effects/save path new-set]]))
                         ::ob/keydown-input
                         (fn [_ {:keys [path key]}]
                           (case key
                             "Escape" [[:effects/save (conj path :value) ""]]
                             nil))
                         ::ob/keydown-input-client
                         ^::🪐/client (fn [_ {:keys [key popover-id child-id] mods :key-modifiers}]
                                        (cond
                                          (= key "Escape")    [[:event/prevent-default]
                                                               [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                                                               [:dom-node/blur]]
                                          (= key "ArrowDown") [[:event/prevent-default]
                                                               (when child-id
                                                                 [:dom-node/focus {:node [:document/element-by-id child-id]}])]
                                          (and (mods :shift)
                                               (= key "Tab")) [[:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]]
                                          :else               nil))
                         ::ob/keydown-choice-item
                         (fn [_ {:keys [on-enter key]}]
                           (case key
                             "Enter" on-enter
                             nil))
                         ::ob/keydown-choice-item-client
                         ^::🪐/client (fn [_ {:keys [key popover-id prev-id next-id input-id on-enter]}]
                                        (case key
                                          "Escape"    [[:event/prevent-default]
                                                       [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                                                       [:dom-node/blur]]
                                          "Enter"     [[:event/prevent-default]
                                                       [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                                                       [:dom-node/blur]]
                                          "ArrowUp"   [[:event/prevent-default]
                                                       (if prev-id
                                                         [:dom-node/focus {:node [:document/element-by-id prev-id]}]
                                                         [:dom-node/focus {:node [:document/element-by-id input-id]}])]
                                          "ArrowDown" [[:event/prevent-default]
                                                       (when next-id
                                                         [:dom-node/focus {:node [:document/element-by-id next-id]}])]
                                          "Tab"       [(when-not next-id
                                                         [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}])]
                                          nil))}
   :nexus/placeholders  {:event.target/value
                         ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                        (some-> dom-event .-target .-value))
                         :event.target/checked
                         ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                        (some-> dom-event .-target .-checked))
                         :event.target/scroll-top
                         ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                        (some-> dom-event .-target .-scrollTop))
                         :event.target/scroll-left
                         ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                        (some-> dom-event .-target .-scrollLeft))
                         :event/content-width
                         ^::🪐/client (fn [{:replicant/keys [node]}]
                                        (some-> node .-contentRect .-width))
                         :event/content-height
                         ^::🪐/client (fn [{:replicant/keys [node]}]
                                        (some-> node .-contentRect .-height))
                         :event/key
                         ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                        (.-key dom-event))
                         :event/key-modifiers
                         ^::🪐/client (fn [{:replicant/keys [dom-event] :as args}]
                                        (println "EVENT" dom-event)
                                        (into #{}
                                              (filter some?)
                                              [(when (.-shiftKey dom-event) :shift)
                                               (when (.-altKey dom-event) :alt)
                                               (when (.-ctrlKey dom-event) :ctrl)]))
                         :fmt/as-long
                         ^::🪐/client (fn [_ value]
                                        (or (some-> value parse-long) 0))
                         :fmt/as-double
                         ^::🪐/client (fn [_ value]
                                        (or (some-> value parse-double) 0))
                         :document/element-by-id
                         ^::🪐/client #?(:clj (fn [])
                                         :cljs (fn [_ id]
                                                 (js/document.getElementById id)))}})
