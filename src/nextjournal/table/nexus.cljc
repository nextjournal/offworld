(ns nextjournal.table.nexus
  (:require
   [nexus.registry :as nxr]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.table.ui.omnibox :as-alias ob]
   [nextjournal.offworld :as-alias 🪐]))

(defn get-node [{:as ctx :keys [dispatch-data]}]
    (-> (or dispatch-data ctx) :replicant/dom-event))

(defn get-evt [{:as ctx :keys [dispatch-data]}]
  (-> (or dispatch-data ctx) :replicant/dom-event))

#?(:cljs
   (def client
     {:nexus/system->state deref
      :nexus/effects
      {:event/prevent-default #(.preventDefault (get-evt %))
       :dom-node/focus        (fn [ctx _ & {:keys [node]}] (.focus (or node (get-node ctx))))
       :dom-node/blur         (fn [ctx _ & {:keys [node]}] (.blur (or node (get-node ctx))))
       :dom-node/show-popover (fn [ctx _ & {:keys [node]}] (.showPopover (or node (get-node ctx))))
       :dom-node/hide-popover (fn [ctx _ & {:keys [node]}] (.hidePopover (or node (get-node ctx))))
       :browser/alert         (fn [_ _ s] (js/alert s))
       :dom-node/set-checked  (fn [ctx _ & {:keys [node value]}]
                                (set! (.-checked (or node (get-node ctx))) value))
       :input/clear           (fn [ctx _ & {:keys [node value]}]
                                (set! (.-value (or node (get-node ctx))) value))}

      :nexus/actions
      {::ob/keydown-input
       (fn [_ {:keys [key popover-id child-id path] mods :key-modifiers}]
         (cond
           (= key "Escape")    [[:event/prevent-default]
                                [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                                [:dom-node/blur]
                                [:input/clear {:node [:document/element-by-id popover-id]}]
                                [:effects/save (conj path :value) ""]]
           (= key "ArrowDown") [[:event/prevent-default]
                                (when child-id
                                  [:dom-node/focus {:node [:document/element-by-id child-id]}])]
           (and (mods :shift)
                (= key "Tab")) [[:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]]
           :else               nil))
       ::ob/keydown-choice-item
       (fn [_ {:keys [key popover-id prev-id next-id input-id on-enter]}]
         (case key
           "Escape"    [[:event/prevent-default]
                        [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                        [:dom-node/blur]]
           "Enter"     (into
                        [[:event/prevent-default]
                         [:dom-node/hide-popover {:node [:document/element-by-id popover-id]}]
                         [:dom-node/blur]]
                        on-enter)
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

      :nexus/placeholders
      {:event.target/value       #(some-> % get-evt .-target .-value)
       :event.target/checked     #(some-> % get-evt .-target .-checked)
       :event.target/scroll-top  #(some-> % get-evt .-target .-scrollTop)
       :event.target/scroll-left #(some-> % get-evt .-target .-scrollLeft)
       :event/content-width      #(some-> % get-node .-contentRect .-width)
       :event/content-height     #(some-> % get-node .-contentRect .-height)
       :event/key                #(.-key (get-evt %))
       :event/key-modifiers      (fn [{:replicant/keys [dom-event]}]
                                   (into #{}
                                         (filter some?)
                                         [(when (.-shiftKey dom-event) :shift)
                                          (when (.-altKey dom-event) :alt)
                                          (when (.-ctrlKey dom-event) :ctrl)]))
       :fmt/as-long              (fn [_ value] (or (some-> value parse-long) 0))
       :fmt/as-double            (fn [_ value] (or (some-> (parse-double value)) 0))
       :document/element-by-id   (fn [_ id] (js/document.getElementById id))}}))

(def server
  {:nexus/system->state deref
   :nexus/effects       {:effects/save
                         ^:nexus/batch
                         (fn [_ system path-vs]
                           (swap! system
                                  (fn [state]
                                    (reduce (fn [acc [path v]]
                                              (assoc-in acc path v))
                                            state path-vs))))}
   :nexus/actions
   {::ng/scroll              (fn [_ path top left]
                               [[:effects/save (concat path [:scroll-top]) top]
                                [:effects/save (concat path [:scroll-left]) left]])
    ::ng/resize              (fn [_ width height]
                               [[:effects/save [:grid :width] width]
                                [:effects/save [:grid :height] height]])
    ::ob/add-filter          (fn [state path value]
                               (let [p           (conj path :filters)
                                     old-filters (get-in state p #{})
                                     new-filters (conj old-filters value)]
                                 [[:effects/save (conj path :filters) new-filters]]))
    ::ob/remove-filter       (fn [state path value]
                               (let [p           (conj path :filters)
                                     old-filters (get-in state p #{})
                                     new-filters (disj old-filters value)]
                                 [[:effects/save (conj path :filters) new-filters]]))
    ::ob/toggle-choice       (fn [state {:keys [path k value]}]
                               (let [old-set (get-in state path #{})
                                     new-set (if value
                                               (disj old-set k)
                                               (conj old-set k))]
                                 [[:effects/save path new-set]]))}})
