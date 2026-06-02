(ns nextjournal.offworld.demo.offline
  (:require
   [nextjournal.offworld.util :as ou]
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as ow]
   [nexus.core :as nexus]))

(def !online? (atom true))
(def !action-log (atom nil))
(def !system (atom nil))
(def !id->sync-state (atom nil))

(defn render [{::k/keys [stem path config]
               :keys    [render-fn dom-node]}
              & [new-stem]]
  #_(let [render-fn (get-in @ow/registry [:render-fn render-fn])
        state     {::k/stem (merge (or new-stem stem)
                                   {::ow/offline?         true
                                    ::ow/last-server-stem stem})}]
    (rdom/render (.-firstElementChild dom-node)
                 (render-fn (k/+ state path config)))))

(defn flush-replicant!
  "Clear replicant's vdom - otherwise, any morphs done
  since the last render could break replicant's reconciler."
  []
  #_(vreset! rdom/state {}))

(defn go-offline! []
  (let [nodes          (array-seq (js/document.querySelectorAll "[data-offworld-sync]"))
        sync-states    (for [node nodes]
                         (merge (ou/deserialize (.getAttribute node "data-offworld-sync"))
                                {:dom-node node}))
        id->sync-state (zipmap (map :id sync-states) sync-states)
        offline-stem   (reduce (fn [acc {:keys [select-paths] ::k/keys [stem]}]
                                 (reduce #(assoc-in %1 %2 (get-in stem %2)) acc select-paths))
                               {}
                               sync-states)]
    (flush-replicant!)
    (reset! ow/online? false)
    (reset! !online? false)
    (reset! !id->sync-state id->sync-state)
    (reset! !system offline-stem)
    (doall (map render sync-states (repeat offline-stem)))))

(defn go-online! []
  (js/fetch
   (str "/offworld-go-online?action-log=" @!action-log "&state=" @!system))
  (flush-replicant!)
  (reset! ow/online? true)
  (reset! !online? true)
  (reset! !system nil)
  (reset! !id->sync-state nil)
  (reset! !action-log nil))

(defonce connection-listeners
  (do (.addEventListener js/window "online" go-online!)
      (.addEventListener js/window "offline" go-offline!)))

(add-watch !system ::offline-render
           #(doseq [sync-state (vals @!id->sync-state)]
              (render sync-state %4)))

(defn offline-capable [_ hiccup] hiccup)

(defn offline-dispatch [dispatch-data actions]
  (let [client-nexus      (ow/get-client-nexus)
        server-nexus      (ow/get-server-nexus)
        client-actions    (filterv #(or (ow/client-action? client-nexus %)
                                        (ow/client-effect? client-nexus %)) actions)
        server-actions    (filterv #(or (ow/server-action? server-nexus %)
                                        (ow/server-effect? server-nexus %)) actions)
        {:keys [effects]} (nexus/expand-actions client-nexus nil client-actions dispatch-data)
        server-effects    (filterv #(ow/server-effect? server-nexus %) effects)
        actions-to-log    (seq (concat server-effects server-actions))]
    (swap! !action-log (fnil into []) actions-to-log)
    (nexus/dispatch (ow/get-client-nexus {:mode :csr}) !system dispatch-data actions)))

(comment
  (go-offline!)
  (go-online!)
  @!action-log
  nil)
