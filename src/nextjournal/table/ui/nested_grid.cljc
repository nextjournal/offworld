(ns nextjournal.table.ui.nested-grid
  (:require
   [nextjournal.baseline :as k]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.table.ui.nested-grid.util :as ngu]
   [nextjournal.table.ui.holiday :as 🎄]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]
   [datastar :as-alias 🚀]))

(defn demo-header-tree [direction]
  (into [:root]
        (map (fn [a]
               (into []
                     (map (fn [b]
                            {:id   (keyword (str (case direction
                                                   :row "r"
                                                   (:column :col) "c") a b))
                             :size (rand-nth [20 25 30 35 40])}))
                     (range 10))))
        (range 50)))

(def demo-row-tree (demo-header-tree :row))
(def demo-col-tree (demo-header-tree :row))

(nxr/register-action! ::init-local
  (fn [_ path]
    [[:effects/save path {:size-cache (volatile! {})}]]))

(defn nested-grid [{:as      state
                    ::k/keys [stem path]
                    :keys    [row-tree column-tree overscan]
                    :or      {overscan 100}}]
  (let [{:keys [size-cache
                scroll-top
                scroll-left]
         :or   {scroll-top 0 scroll-left 0}} (k/local state)]
    (if-not size-cache
      [:div {:replicant/on-mount [[::init-local path]]
             ::🚀/data-init      [^::🪐/server [::init-local path]]}]
      (let [height 800
            width  1200
            {:as            row-traversal
             row-paths      :header-paths
             row-grid-names :grid-names
             row-sum-size   :sum-size} (ngu/window
                                        {:header-tree  row-tree
                                         :window-start (- scroll-top overscan)
                                         :window-end   (+ scroll-top height overscan)
                                         :size-cache   size-cache})
            {:as            col-traversal
             col-paths      :header-paths
             col-grid-names :grid-names
             col-sum-size   :sum-size} (ngu/window
                                        {:header-tree  column-tree
                                         :window-start (- scroll-left overscan)
                                         :window-end   (+ scroll-left width overscan)
                                         :size-cache   size-cache})
            showing?                   (comp (some-fn :show? :leaf?) meta)]
        [:div {:id    :grid
               :on    {:scroll
                       ^{::🚀/modifiers [:throttle.100ms]}
                       [[::ng/scroll path
                         [:event.target/scroll-top]
                         [:event.target/scroll-left]]]}
               :style {:height   height
                       :width    width
                       :resize   :both
                       :overflow :auto}}
         (into [:div {:style {:width                 col-sum-size
                              :height                row-sum-size
                              :display               :grid
                              :overflow-anchor       :none
                              :grid-template-rows    (ngu/grid-template row-traversal)
                              :grid-template-columns (ngu/grid-template col-traversal)}}]
               (for [ri    (range (count row-paths))
                     ci    (range (count col-paths))
                     :let  [row-path      (nth row-paths ri)
                            col-path      (nth col-paths ci)
                            row-grid-name (nth row-grid-names ri)
                            col-grid-name (nth col-grid-names ci)]
                     :when (and (showing? row-path)
                                (showing? col-path))]
                 [:div {:replicant/key [row-path col-path]
                        :style         {:border-right      "1px solid grey"
                                        :border-bottom     "1px solid grey"
                                        :font-size         7
                                        :grid-row-start    row-grid-name
                                        :grid-column-start col-grid-name}}
                  (:id (last row-path)) " "
                  (:id (last col-path))
                  (🎄/get-icon stem)]))]))))
