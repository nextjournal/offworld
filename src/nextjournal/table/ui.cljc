(ns nextjournal.table.ui
  (:require
   [nextjournal.table.ui.omnibox :as omnibox-ui]
   [nextjournal.table.ui.nested-grid :as ng]
   [nextjournal.table.ui.holiday :as holiday]
   [nextjournal.ductile.load-builder :as lb]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo.mapbox :as mb]))

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
    (holiday/panel (k/+ state [:panel]))]])
