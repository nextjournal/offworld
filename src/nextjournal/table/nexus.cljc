(ns nextjournal.table.nexus
  (:require
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as-alias 🪐]))

(def nexus
  {:nexus/system->state deref
   :nexus/effects       {:effects/save
                         ^:nexus/batch
                         (fn [_ store path-vs]
                           (swap! store
                                  (fn [state]
                                    (reduce (fn [acc [path v]]
                                              (assoc-in acc path v))
                                            state path-vs))))
                         :event/prevent-default
                         ^:🪐/client (fn [{{:replicant/keys [dom-event]} :dispatch-data}]
                                       (.preventDefault dom-event))}
   :nexus/actions       {:actions/inc (fn [state path]
                                        [[:effects/save path (+ (:step state) (get-in state path))]])
                         ::ng/scroll  (fn [_ top left]
                                        [[:effects/save [:grid :scroll-top] top]
                                         [:effects/save [:grid :scroll-left] left]])
                         ::ng/resize  (fn [_ width height]
                                        [[:effects/save [:grid :width] width]
                                         [:effects/save [:grid :height] height]])}
   :nexus/placeholders  {:event.target/value
                         ^:🪐/client (fn [{:replicant/keys [dom-event]}]
                                       (some-> dom-event .-target .-value))
                         :event.target/scroll-top
                         ^:🪐/client (fn [{:replicant/keys [dom-event]}]
                                       (some-> dom-event .-target .-scrollTop))
                         :event.target/scroll-left
                         ^:🪐/client (fn [{:replicant/keys [dom-event]}]
                                       (some-> dom-event .-target .-scrollLeft))
                         :event/content-width
                         ^:🪐/client (fn [{:replicant/keys [dom-node]}]
                                       (some-> dom-node .-contentRect .-width))
                         :event/content-height
                         ^:🪐/client (fn [{:replicant/keys [dom-node]}]
                                       (some-> dom-node .-contentRect .-height))
                         :fmt/as-long
                         ^:🪐/client (fn [_ value]
                                       (or (some-> value parse-long) 0))
                         :fmt/as-double
                         ^:🪐/client (fn [_ value]
                                       (or (some-> value parse-double) 0))}})
