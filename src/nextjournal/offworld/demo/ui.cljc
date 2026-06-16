(ns nextjournal.offworld.demo.ui
  (:require
   [nextjournal.offworld.demo.ui.omnibox :as omnibox-ui]
   [nextjournal.offworld.demo.ui.nested-grid :as ng]
   [nextjournal.offworld.demo.ui.holiday :as holiday]
   [nextjournal.offworld.demo.load-builder :as lb]
   [nextjournal.offworld.stem :as 🌿]
   [nextjournal.offworld.demo.mapbox :as mb]
   [nextjournal.offworld.demo.scan :as scan]
   [nextjournal.offworld.demo.offline :as 🌠]))

(defn render [{:as state ::🌿/keys [stem]}]
  [:main {:id "app"}
   [:div.flex
    (mb/mapbox (🌿/+ state [:mapbox]))
    [:div
     [:div.flex
      (map
       (fn [id]
         (omnibox-ui/omnibox
          (🌿/+ state [:header-fields id]
               {:choices (lb/get-choices stem id)})))
         (lb/get-header-fields stem))]
     (ng/nested-grid
      (🌿/+ state [:grid]
           {:row-tree    ng/demo-row-tree
            :column-tree ng/demo-col-tree}))]
  (🌠/offline-capable
     {:id                "scan-game-offline"
      :render-fn         #'scan/offline-game
      :select-paths      #{[::scan/scans]
                           [::scan/plates]}
      ::🌿/path           [:scan-game]
      ::🌿/stem           stem
      #_#_:cache-queries [#'scan/get-scans #'scan/get-plates]}
     (scan/game (🌿/+ state [:scan-game])))
    (holiday/panel (🌿/+ state [:panel]))]])
