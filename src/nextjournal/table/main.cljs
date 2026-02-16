(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.table.ui :as ui]
   [replicant.dom :as r]
   [nextjournal.table.util :as u]
   nextjournal.table.nexus
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]
   [nextjournal.baseline :as k]))

(defonce system
  (atom (u/init-state)))

(def nexus+registry (merge-with merge nextjournal.table.nexus/nexus (nxr/get-registry)))

(r/set-dispatch!
 #(nexus/dispatch nexus+registry system %1 %2))

(🪐/register-nexus! nexus+registry)

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! system update :dev/load inc))

(defn main []
  (when-not (str/includes? js/document.location.search "?ssr=true")
    (add-watch system
               ::render
               (fn [_ _ _ new-state]
                 (r/render root-el (ui/render new-state))))
    (after-load)))

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "App state" system)
  nil)
