(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.table.nexus :as table.nexus]
   [nextjournal.table.ui :as ui]
   [replicant.dom :as r]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.offline :as 🌠]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo :as demo]))

(defonce system
  (atom (demo/init-state {})))

(🪐/register-client-nexus! table.nexus/client (nxr/get-registry))
(🪐/register-server-nexus! table.nexus/server (nxr/get-registry))

(r/set-dispatch!
 (fn [dispatch-data actions]
   (if @🪐/online?
     (nexus/dispatch (🪐/get-client-nexus) system dispatch-data actions)
     (🪐/offline-dispatch dispatch-data actions))))

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! system update :dev/load inc))

(defn main []
  (reset! 🪐/mode (if (str/includes? js/document.location.search "?ssr=true") :ssr :csr))
  (when (= :csr @🪐/mode)
    (add-watch system
               ::render
               (fn [_ _ _ new-state]
                 (r/render root-el (ui/render (k/init-state new-state)))))
    (after-load)))

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "App state" system)
  nil)
