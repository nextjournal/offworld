(ns nextjournal.table.nexus
  (:require
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.table.ui.omnibox :as-alias ob]
   [nextjournal.baseline :as-alias k]))

(defn get-node [{:as ctx :keys [dispatch-data]}]
    (-> (or dispatch-data ctx) :replicant/node))

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
       :dom-node/show-modal   (fn [ctx]
                                (get-node ctx)
                                (.showModal (get-node ctx)))
       :browser/alert         (fn [_ _ s] (js/alert s))
       :dom-node/set-checked  (fn [ctx _ & {:keys [node value]}]
                                (set! (.-checked (or node (get-node ctx))) value))
       :input/clear           (fn [ctx _ & {:keys [node value]}]
                                (set! (.-value (or node (get-node ctx))) value))}

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
                                            state path-vs))))
                         :effects/conj
                         ^:nexus/batch
                         (fn [_ system path-vs]
                           (swap! system
                                  (fn [state]
                                    (reduce (fn [acc [path v default-coll]]
                                              (update-in acc path (fnil conj (or default-coll [])) v))
                                            state path-vs))))}})
