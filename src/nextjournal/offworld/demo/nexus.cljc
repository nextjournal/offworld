(ns nextjournal.offworld.demo.nexus
  #_(:require
   [nextjournal.offworld.demo.ui.nested-grid :as-alias ng]
   [nextjournal.offworld.demo.ui.omnibox :as-alias ob]))

(defn get-node [ctx]
  (:replicant/node (:dispatch-data ctx ctx)))

(defn get-evt [ctx]
  (:replicant/dom-event (:dispatch-data ctx ctx)))

#?(:cljs
   (def client
     {:nexus/system->state deref
      :nexus/effects
      {:event/prevent-default #(.preventDefault (get-evt %))
       :node/focus            (fn [ctx _ & [node]] (.focus (or node (get-node ctx))))
       :node/blur             (fn [ctx _ & [node]] (.blur (or node (get-node ctx))))
       :node/show-popover     (fn [ctx _ & [node]] (.showPopover (or node (get-node ctx))))
       :node/hide-popover     (fn [ctx _ & [node]] (.hidePopover (or node (get-node ctx))))
       :node/show-modal       (fn [ctx]
                                (get-node ctx)
                                (.showModal (get-node ctx)))
       :browser/alert         (fn [_ _ s] (js/alert s))
       :node/set-checked      (fn [ctx _ node value]
                                (set! (.-checked (or node (get-node ctx))) value))
       :input/clear           (fn [ctx _ node]
                                (set! (.-value (or node (get-node ctx))) nil))}

      :nexus/placeholders
      {:event.target/value       #(some-> % get-evt .-target .-value)
       :event.target/checked     #(some-> % get-evt .-target .-checked)
       :event.target/scroll-top  #(some-> % get-evt .-target .-scrollTop)
       :event.target/scroll-left #(some-> % get-evt .-target .-scrollLeft)
       :event/content-width      #(some-> % get-node .-contentRect .-width)
       :event/content-height     #(some-> % get-node .-contentRect .-height)
       :event/key                #(.-key (get-evt %))
       :event/key-modifiers      (fn [dispatch-data]
                                   (let [dom-event (:replicant/dom-event dispatch-data)]
                                     [(when (.-shiftKey dom-event) :shift)
                                      (when (.-altKey dom-event)   :alt)
                                      (when (.-ctrlKey dom-event)  :ctrl)]))
       :document/element-by-id   (fn [_ id] (js/document.getElementById id))}}))

(def server
  #?(:clj
     {:nexus/system->state deref
      :nexus/effects       {:effects/save
                            ^:nexus/batch
                            (fn [_ system path-vs]
                              (swap! system inc))
                            :effects/conj
                            ^:nexus/batch
                            (fn [_ system path-vs]
                              (swap! system
                                     (fn [state]
                                       (reduce (fn [acc [path v default-coll]]
                                                 (update-in acc path (fnil conj (or default-coll [])) v))
                                               state path-vs))))}}
     :cljs
     {:nexus/system->state deref
      :nexus/effects       {:effects/save (fn [])
                            :effects/conj (fn [])}}))
