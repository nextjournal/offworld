(ns nextjournal.offworld.demo.ui
  (:require
   [nextjournal.offworld.demo.ui.omnibox :as omnibox-ui]
   [nextjournal.offworld.demo.ui.nested-grid :as ng]
   [nextjournal.offworld.demo.ui.holiday :as holiday]
   [nextjournal.offworld.demo.load-builder :as lb]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo.mapbox :as mb]
   [nextjournal.offworld.demo.scan :as scan]
   [nextjournal.offworld.demo.offline :as oo]))

(defn render [{:as state ::k/keys [stem]}]
  [:main {:id "app"}
   [:div.flex
    (mb/mapbox (k/+ state [:mapbox]))
    [:div
     [:div.flex
      (for [id (lb/get-header-fields stem)]
        (omnibox-ui/omnibox
         (k/+ state [:header-fields id]
              {:choices (lb/get-choices stem id)})))]
     (ng/nested-grid
      (k/+ state [:grid]
           {:row-tree    ng/demo-row-tree
            :column-tree ng/demo-col-tree}))]
    (scan/game (k/+ state [:scan-game]))
    (holiday/panel (k/+ state [:panel]))]])
