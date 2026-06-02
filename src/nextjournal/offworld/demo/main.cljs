(ns nextjournal.offworld.demo.main
  (:require
   [clojure.string :as str]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.offworld.demo.nexus :as demo.nexus]
   [nextjournal.offworld.demo.ui :as ui]
   [nextjournal.offworld.demo.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as ow]
   [nextjournal.offworld.demo.offline :as oo]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo :as demo]))

(defonce system
  (atom (demo/init-state {})))

(ow/register-client-nexus! demo.nexus/client (nxr/get-registry))
(ow/register-server-nexus! demo.nexus/server (nxr/get-registry))

#_(r/set-dispatch!
         (fn [dispatch-data actions]
           (if @ow/online?
             (nexus/dispatch (ow/get-client-nexus) system dispatch-data actions)
             (oo/offline-dispatch dispatch-data actions))))

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! system update :dev/load inc))

(defn main []
  (reset! ow/mode (if (str/includes? js/document.location.search "?ssr=true") :ssr :csr))
  (when (= :csr @ow/mode)
    (add-watch system
               ::render
               (fn [_ _ _ new-state]
                 (r/render root-el (ui/render (k/init-state new-state)))))
    (after-load)))

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "App state" system)
  nil)
