(ns nextjournal.offworld.demo.csr
  (:require
   [replicant.dom :as r]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo.ui :as ui]
   [nextjournal.offworld.demo :as demo]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.demo.offline :as 🌠]
   [nexus.registry :as nxr]
   nextjournal.offworld.demo.nexus))

(defonce system
  (atom (demo/init-state {})))

(r/set-dispatch!
 (fn [dispatch-data actions]
   (println actions)
     (if @🪐/online?
       (nxr/dispatch system dispatch-data actions)
       (🌠/offline-dispatch dispatch-data actions))))

(defonce root-el
  (js/document.getElementById "app"))

(defn render! [state]
  (r/render root-el (ui/render (k/init-state state))))

(defn start! []
  (js/console.log "CSR BUNDLE STARTING")
  (add-watch system ::render
             (fn [_ _ _ new-state]
               (render! new-state)))
  (render! @system))

(defn ^:dev/after-load after-load []
  (when (= :csr (🪐/get-ux))
    (swap! system update :dev/load inc)))
