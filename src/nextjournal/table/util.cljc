(ns nextjournal.table.util
  (:require
   [nextjournal.ductile.load-builder :as load-builder]))

(defn init-store
  []
  {:grid {:row-tree    (into [:root]
                             (map (fn [a]
                                    (into []
                                          (map (fn [b]
                                                 {:id   (keyword (str "r" a b))
                                                  :size (rand-nth [20 25 30 35
                                                                   40])}))
                                          (range 10))))
                             (range 50))
          :column-tree (into [:root]
                             (map (fn [a]
                                    (into []
                                          (map (fn [b]
                                                 {:id   (keyword (str "c" a b))
                                                  :size (rand-nth [20 25 30 35
                                                                   40])}))
                                          (range 10))))
                             (range 50))
          :size-cache  (volatile! {})}
   :omnibox
   {[:transport/destination :address/city]
    {:id          [:transport/destination :address/city]
     :choices     (get-in load-builder/filters [[:transport/destination :address/city] 1])}
    [:transport/destination :address/postcode]
    {:id          [:transport/destination :address/postcode]
     :choices     (get-in load-builder/filters [[:transport/destination :address/postcode] 1])}}})
