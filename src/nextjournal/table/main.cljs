(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [nexus.core :as nexus]
   [nextjournal.table.ui :as ui]
   [replicant.dom :as r]
   [nextjournal.table.util :as u]
   nextjournal.table.nexus
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]))

(defonce !store
  (atom (u/init-store)))

(r/set-dispatch! #(nexus/dispatch nextjournal.table.nexus/nexus !store %1 %2))

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! !store update :dev/load inc))

(🪐/register-nexus! nextjournal.table.nexus/nexus)

(defn main []
  (when-not (str/includes? js/document.location.search "?ssr=true")
    (add-watch !store ::render (fn [_ _ _ new-state]
                                 (r/render root-el (ui/render new-state))))
    (after-load)))
