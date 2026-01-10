(ns nextjournal.table.ui.nested-grid
  (:require
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.table.ui.nested-grid.util :as ngu]))

(defn nested-grid [{:keys [row-tree column-tree scroll-top scroll-left overscan size-cache]
                    :or   {scroll-top 0 scroll-left 0 overscan 800}}]
  (let [height                     800
        width                      1200
        {:as            row-traversal
         row-paths      :header-paths
         row-keypaths   :keypaths
         row-grid-names :grid-names
         row-sum-size   :sum-size}   (ngu/window
                                      {:header-tree  row-tree
                                       :window-start (- scroll-top overscan)
                                       :window-end   (+ scroll-top height overscan)
                                       :size-cache   size-cache})
        {:as            col-traversal
         col-paths      :header-paths
         col-keypaths   :keypaths
         col-grid-names :grid-names
         col-sum-size   :sum-size} (ngu/window
                                    {:header-tree  column-tree
                                     :window-start (- scroll-left overscan)
                                     :window-end   (+ scroll-left width overscan)
                                     :size-cache   size-cache})
        showing?                   (comp (some-fn :show? :leaf?) meta)]
    [:div {:on    {:scroll ^{:datastar/modifiers [:debounce.500ms]}
                   [[::ng/scroll]]}
           :style {:height   height
                   :width    width
                   :overflow :auto}}
     [:div {:style {:width                 col-sum-size
                    :height                row-sum-size
                    :display               :grid
                    :overflow-anchor       :none
                    :grid-template-rows    (ngu/grid-template row-traversal)
                    :grid-template-columns (ngu/grid-template col-traversal)}}
      (for [ri    (range (count row-paths))
            ci    (range (count col-paths))
            :let  [row-path      (nth row-paths ri)
                   col-path      (nth col-paths ci)
                   row-keypath   (nth row-keypaths ri)
                   col-keypath   (nth col-keypaths ci)
                   row-grid-name (nth row-grid-names ri)
                   col-grid-name (nth col-grid-names ci)]
            :when (and (showing? row-path)
                       (showing? col-path))]
        [:div {:style         {:border-right      "1px solid grey"
                               :border-bottom     "1px solid grey"
                               :font-size         7
                               :grid-row-start    row-grid-name
                               :grid-column-start col-grid-name}}
         (last row-path) " " (last col-path)])]]))
