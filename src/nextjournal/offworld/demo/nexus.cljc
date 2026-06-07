(ns nextjournal.offworld.demo.nexus
  (:require
   [nexus.registry :as nxr]
   [nextjournal.offworld.demo.ui.nested-grid :as-alias ng]
   [nextjournal.offworld.demo.ui.omnibox :as-alias ob]
   [nextjournal.offworld :as-alias 🪐]
   nextjournal.offworld.demo.mapbox
   nextjournal.offworld.demo.offline
   nextjournal.offworld.demo.scan))

(defn get-node [ctx]
  (:replicant/node (:dispatch-data ctx ctx)))

(defn get-evt [ctx]
  (:replicant/dom-event (:dispatch-data ctx ctx)))

(defn reg-many [m]
  (let [system->state (:nexus/system->state m)
        actions       (:nexus/actions m {})
        effects       (:nexus/effects m {})
        placeholders  (:nexus/placeholders m {})]
    (when system->state (nxr/register-system->state! system->state))
    (run! (fn [[k v]] (nxr/register-action! k v)) actions)
    (run! (fn [[k v]] (nxr/register-effect! k v)) effects)
    (run! (fn [[k v]] (nxr/register-placeholder! k v)) placeholders)))

(def save-batch
  ^:nexus/batch ^::🪐/server
  (fn [_ system path-vs]
    (swap! system
           (fn [state]
             (reduce (fn [acc [path v]]
                       (assoc-in acc path v))
                     state path-vs)))))

(def conj-batch
  ^:nexus/batch ^::🪐/server
  (fn [_ system path-vs]
    (swap! system
           (fn [state]
             (reduce (fn [acc [path v default-coll]]
                       (update-in acc path (fnil conj (or default-coll [])) v))
                     state path-vs)))))

(reg-many
 {:nexus/system->state deref
  :nexus/effects
  {:event/prevent-default #(.preventDefault (get-evt %))
   :node/focus            (fn focus
                            ([ctx _] (.focus (get-node ctx)))
                            ([ctx _ node] (.focus node)))
   :node/blur             (fn blur
                            ([ctx _] (.blur (get-node ctx)))
                            ([ctx _ node] (.blur node)))
   :node/show-popover     (fn show-popover
                            ([ctx _] (.showPopover (get-node ctx)))
                            ([ctx _ node] (.showPopover node)))
   :node/hide-popover     (fn hide-popover
                            ([ctx _] (.hidePopover (get-node ctx)))
                            ([ctx _ node] (.hidePopover node)))
   :node/show-modal       (fn [ctx]
                           #?(:cljs (js/console.log (get-node ctx)))
                            (.showModal (get-node ctx)))
   :browser/alert         (fn [_ _ s] #?(:cljs (js/alert s)))
   :node/set-checked      (fn [ctx _ node value]
                            (set! (.-checked (or node (get-node ctx))) value))
   :input/clear           (fn [ctx _ node]
                            (set! (.-value (or node (get-node ctx))) nil))
   :effects/save          save-batch
   :effects/conj          conj-batch}

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
   :document/element-by-id   (fn [_ id] #?(:cljs (js/document.getElementById id)))}})

(def server {})
