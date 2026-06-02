(ns nextjournal.offworld.demo.ui
  (:require
   [nextjournal.offworld.demo.ui.omnibox :as omnibox-ui]
   [nextjournal.offworld.demo.ui.nested-grid :as ng]
   [nextjournal.offworld.demo.ui.holiday :as holiday]
   [nextjournal.offworld.demo.load-builder :as lb]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo.mapbox :as mb]
   [nextjournal.offworld.demo.scan :as scan]
   #_[nextjournal.offworld.demo.offline :as 🌠]))

(defn render [{:as state ::k/keys [stem]}]
  [:main {:id "app"}
   [:div.flex
    (mb/mapbox (k/+ state [:mapbox]))
    [:div
     [:div.flex
      (map
       (fn [id]
         (omnibox-ui/omnibox
          (k/+ state [:header-fields id]
               {:choices (lb/get-choices stem id)})))
         (lb/get-header-fields stem))]
     (ng/nested-grid
      (k/+ state [:grid]
           {:row-tree    ng/demo-row-tree
            :column-tree ng/demo-col-tree}))]
    #_(🌠/offline-capable
       {:id                "scan-game-offline"
        :render-fn         #'scan/offline-game
        :select-paths      #{[::scan/scans]
                             [::scan/plates]}
        ::k/path           [:scan-game]
        ::k/stem           stem
        #_#_:cache-queries [#'scan/get-scans #'scan/get-plates]}
       (scan/game (k/+ state [:scan-game])))
    (holiday/panel (k/+ state [:panel]))]])
