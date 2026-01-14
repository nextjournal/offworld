(ns nextjournal.table.nexus
  (:require
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as-alias 🪐]))

(def nexus
  {:nexus/system->state deref
   :nexus/effects       {:effects/save (fn [_ store path value]
                                         (swap! store assoc-in path value))
                         :event/prevent-default
                         ^:🪐/client (fn [{{:replicant/keys [dom-event]} :dispatch-data}]
                                       (.preventDefault dom-event))}
   :nexus/actions       {:actions/inc (fn [state path]
                                        [[:effects/save path (+ (:step state) (get-in state path))]])
                         ::ng/scroll  (fn [{:keys [grid]} top left]
                                        [[:effects/save [:grid] (merge grid {:scroll-top  top
                                                                             :scroll-left left})]])
                         ::ng/resize  (fn [{:keys [grid]} width height]
                                        [[:effects/save [:grid] (merge grid {:width  width
                                                                             :height height})]])}
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
