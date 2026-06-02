(ns nextjournal.offworld.demo.load-builder
  (:require
   [clojure.set :as set]
   [nextjournal.offworld.util]
   [nextjournal.baseline :as k :refer [defq]]))

(defn ->v [x] (if (vector? x) x [x]))

(def transports
  [])

(def base-filters [[:make/name {}]
                   [[:transport/origin :address/postcode] {:numeric? true}]
                   [[:transport/origin :address/city] {}]
                   [[:transport/origin :address/name] {}]
                   [[:transport/destination :address/postcode] {:numeric? true}]
                   [[:transport/destination :address/region] {}]
                   [[:transport/destination :address/province-code] {}]
                   [[:transport/destination :address/city] {}]
                   [[:transport/destination :address/name] {}]
                   [:vehicle/model {}]
                   [:contract/name {}]
                   [:vehicle/weight {:numeric? true}]
                   [:load/id {}]
                   [:fahrzeug/parking-spot {}]
                   [:fahrzeug/labels {:set? true}]])

(defq get-transports [stem] (::transports stem))

(defq get-filters [stem]
  (let [transports (get-transports stem)]
    (into {:fahrzeug/priorities [:checkbox "🤝"]}
          (for [[k opts] base-filters]
            [k [:omnibox
                (cond
                  (:set? opts) (apply set/union (map get-in transports (repeat (->v k))))
                  :else        (into (sorted-set) (map get-in transports (repeat (->v k)))))
                opts]]))))

(defq get-header-fields [stem] (::header-fields stem))

(defq get-choices [stem id]
  (-> stem get-filters (get-in [id 1])))

(defn init-state [state]
  (merge
   state
   {::transports    transports
    ::header-fields [[:transport/destination :address/city]
                     [:transport/destination :address/postcode]]}))
