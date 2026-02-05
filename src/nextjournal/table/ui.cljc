(ns nextjournal.table.ui
  (:require
   [nextjournal.table.ui.omnibox :as omnibox-ui]
   [nextjournal.table.ui.nested-grid :as ng]
   [nextjournal.table.ui.holiday :as holiday]
   [nextjournal.ductile.load-builder :as-alias load-builder]
   [nextjournal.baseline :as k]))

(defn render [state]
  [:main {:id "app"}
   [:div.flex
    (for [[_ {:keys [id choices]}] (k/q state ::load-builder/header-fields)]
      (omnibox-ui/omnibox
       (k/+ state [:header-fields id] {:choices choices})))]
   (ng/nested-grid
    (k/+ state [:grid]
         {:row-tree    ng/demo-row-tree
          :column-tree ng/demo-col-tree}))
   (holiday/panel (k/+ state [:panel]))])
