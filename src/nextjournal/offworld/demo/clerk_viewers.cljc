(ns nextjournal.offworld.demo.clerk-viewers
    (:require
     [nextjournal.clerk :as clerk]
     #?(:clj [replicant.string :as rstr])
     [nextjournal.offworld.demo.ui :as ui]
     [nextjournal.offworld.demo.ui.nested-grid.util :as ngu]))

#?(:clj (def replicant-ssr
          {:transform-fn (clerk/update-val (comp clerk/html rstr/render))}))

(defn nested-grid->clerk-table [{:keys [row-depth col-depth corner-viewer
                col-viewer row-viewer cell-viewer
                                        nested-cols nested-rows data]}]
  (let [col-paths   (:header-paths (ngu/window {:header-tree nested-cols}))
        row-paths   (:header-paths (ngu/window {:header-tree nested-rows}))
        nested-head (mapv (fn [ri]
                            (into (mapv (resolve corner-viewer)
                                        (repeat ri)
                                        (range row-depth))
                                  (mapv (resolve col-viewer)
                                        (mapv #(into [] (take (inc ri) %)) col-paths))))
                          (range col-depth))
        nested-rows (mapv (fn [rp]
                            (into (mapv (resolve row-viewer)
                                        (mapv #(into [] (take (inc %) rp) (range col-depth)))
                                  (mapv (resolve cell-viewer)
                                        (repeat data)
                                        (repeat rp)
                                        col-paths)))
                          row-paths)]
    ;; Ideally we'd pass a 2D structure for `:head`. Can clerk support multiple header rows?
    {:nextjournal/value {:head (first nested-head)
                         :rows (concat (rest nested-head) nested-rows)}
     :nextjournal/viewer 'nextjournal.clerk.viewer/table-viewer}))

(def nested-grid
  {:transform-fn (clerk/update-val nested-grid->clerk-table)})
