(ns nextjournal.offworld.demo.offline
  (:require
   [nextjournal.offworld.util :as ou]
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as 🪐]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
))

(def !online? (atom true))
(def !action-log (atom nil))
(def !system (atom nil))
(def !id->sync-state (atom nil))

(defn render [{::k/keys [stem path config]
               :keys    [render-fn dom-node]}
              & [new-stem]]
  (let [render-fn (get-in @🪐/registry [:render-fn render-fn])
        state     {::k/stem (merge (or new-stem stem)
                                   {::🪐/offline?         true
                                    ::🪐/last-server-stem stem})}]
    #_(rdom/render (.-firstElementChild dom-node)
                 (render-fn (k/+ state path config)))))

(defn flush-replicant!
  "Clear replicant's vdom - otherwise, any morphs done
  since the last render could break replicant's reconciler."
  []
  #_(vreset! rdom/state {}))

#_(defn go-offline! []
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
    (reset! 🪐/online? false)
    (reset! !online? false)
    (reset! !id->sync-state id->sync-state)
    (reset! !system offline-stem)
    (doall (map render sync-states (repeat offline-stem)))))

(defn go-online! []
  (js/fetch
   (str "/offworld-go-online?action-log=" @!action-log "&state=" @!system))
  (flush-replicant!)
  (reset! 🪐/online? true)
  (reset! !online? true)
  (reset! !system nil)
  (reset! !id->sync-state nil)
  (reset! !action-log nil))

#_(defonce connection-listeners
  (do (.addEventListener js/window "online" go-online!)
      (.addEventListener js/window "offline" go-offline!)))

(add-watch !system ::offline-render
           #(run! (fn [sync-state] (render sync-state %4))
                  (vals @!id->sync-state)))

(defn offline-capable [_ hiccup] hiccup)

(defn offline-dispatch [dispatch-data actions]
  #_
  (let [nexus          (nxr/get-registry)
        ux             (🪐/get-ux)
        client-ax?     #(🪐/client-handled? ux :nexus/actions nexus %)
        client-fx?     #(🪐/client-handled? ux :nexus/effects nexus %)
        server-ax?     #(🪐/server-handled? ux :nexus/actions nexus %)
        server-fx?     #(🪐/server-handled? ux :nexus/effects nexus %)
        client-ax-fx   (filterv #(or (client-ax? %) (client-fx? %)) actions)
        server-ax-fx   (filterv #(or (server-ax? %) (server-fx? %)) actions)
        xp-fx          (:effects (nexus/expand-actions nexus nil client-ax-fx dispatch-data))
        server-xp-fx   (filterv server-fx? xp-fx)
        actions-to-log (into server-xp-fx server-ax-fx)]
    (swap! !action-log #(into (or % []) actions-to-log))
    (nexus/dispatch nexus !system dispatch-data actions)))

(comment
  #_(go-offline!)
  (go-online!)
  @!action-log
  nil)
