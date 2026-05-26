(ns nextjournal.offworld.demo.load-builder
  (:require
   [nextjournal.offworld.util]
   [nextjournal.baseline :as k :refer [defq]]))

(defn ->v [x] (if (vector? x) x [x]))

(def transports
  [{:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-19T09:39:36.497-00:00"
    :ductile/id #uuid "c71af681-78ed-4157-b6b8-f2cf19fb6322"
    :exit/expected-datetime #inst "2025-12-19T09:01:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-23T09:01:14.204-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/20/5"
    :load/id nil
    :make/name "BMW"
    :transport/destination
      {:address/city "Sprockhövel"
       :address/country #:country{:code "DEU"}
       :address/name "Procar Automobile GmbH\nStandort Sprockhövel"
       :address/postcode "45549"
       :address/street "Eichenhofer Weg 1-7"
       :ductile/id #uuid "e9f94349-6f42-408a-b101-b3e6d8aa52db"
       :elog/adresse-id "0000143547"
       :external.bmw/location-codes ["031143"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-FR: 08:00 - 18:30 Uhr\nkeine Nachtanlieferung!!"
       :location/latitude 51.31888
       :location/longitude 7.27633
       :location/types [:location.type/dealer]}
    :transport/origin {:address/city "Krefeld"
                       :address/name "ARS Altmann AG Krefeld\nAutomobillogistik"
                       :address/postcode "47804"
                       :address/street "Oberbenrader Straße 407"
                       :compound/company #:company{:name
                                                     "ARS Altmann AG Krefeld"}
                       :ductile/id #uuid "4ce0f0c8-3cf6-45d0-bb27-deccba1671ac"
                       :elog/adresse-id "11587"
                       :external.bmw/location-codes ["KRECLU" "KREFEL"]
                       :location/latitude 51.32168
                       :location/longitude 6.51579
                       :location/types [:location.type/compound]}
    :vehicle/model "X1 sDrive18i SAV"
    :vehicle/vin "WBA11EE0305557606"
    :vehicle/weight 1672}
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-19T09:15:57.223-00:00"
    :ductile/id #uuid "3c9cba0d-8bdb-443e-b74d-1dbacd1d91de"
    :exit/expected-datetime #inst "2025-12-19T08:19:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-23T08:19:47.821-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/15/2"
    :load/id nil
    :make/name "BMW"
    :transport/destination
      {:address/city                "Dormagen"
       :address/country             #:country{:code "DEU"}
       :address/name                "Brandenburg Hans GmbH"
       :address/postcode            "41540"
       :address/street              "Lübecker Straße  16"
       :ductile/id                  #uuid "9dba1f50-e0b3-42ab-8398-197fe12b3343"
       :elog/adresse-id             "0000143490"
       :external.bmw/location-codes ["032774"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-FR: 08:00 - 18:00 Uhr\nkeine Nachtanlieferung!!\nkeine Samstaganlieferung!\nkeine Abladung auf Werk-\nstatthof. Mittelstreifen\nauf Straße nutzen."
       :location/latitude           51.09097
       :location/longitude          6.81256
       :location/types              [:location.type/dealer]}
    :transport/origin {:address/city "Krefeld"
                       :address/name "ARS Altmann AG Krefeld\nAutomobillogistik"
                       :address/postcode "47804"
                       :address/street "Oberbenrader Straße 407"
                       :compound/company #:company{:name
                                                     "ARS Altmann AG Krefeld"}
                       :ductile/id #uuid "4ce0f0c8-3cf6-45d0-bb27-deccba1671ac"
                       :elog/adresse-id "11587"
                       :external.bmw/location-codes ["KRECLU" "KREFEL"]
                       :location/latitude 51.32168
                       :location/longitude 6.51579
                       :location/types [:location.type/compound]}
    :vehicle/model "X1 M35i xDrive"
    :vehicle/vin "WBA11EF0205557658"
    :vehicle/weight 1797}
   {:contract/name "Weller Premium"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-22T10:52:58.119-00:00"
    :ductile/id #uuid "1e5a74f3-f94b-42b8-8b62-ddd193841988"
    :exit/expected-datetime #inst "2025-12-22T10:16:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-24T10:16:58.030-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/20/8"
    :load/id nil
    :make/name "BMW"
    :transport/destination {:address/city "Herford"
                            :address/country #:country{:code "DEU"}
                            :address/name "WELLER Premium GmbH"
                            :address/postcode "32051"
                            :address/street "Goebenstr. 92-100"
                            :ductile/id #uuid
                                         "38183a23-a78a-4550-b47a-f855ef7d0032"
                            :elog/adresse-id "0000155554"
                            :external.bmw/location-codes ["023447"]
                            :location/latitude 52.1302034
                            :location/longitude 8.6685545
                            :location/types [:location.type/billing
                                             :location.type/dealer]}
    :transport/origin {:address/city "Krefeld"
                       :address/name "ARS Altmann AG Krefeld\nAutomobillogistik"
                       :address/postcode "47804"
                       :address/street "Oberbenrader Straße 407"
                       :compound/company #:company{:name
                                                     "ARS Altmann AG Krefeld"}
                       :ductile/id #uuid "4ce0f0c8-3cf6-45d0-bb27-deccba1671ac"
                       :elog/adresse-id "11587"
                       :external.bmw/location-codes ["KRECLU" "KREFEL"]
                       :location/latitude 51.32168
                       :location/longitude 6.51579
                       :location/types [:location.type/compound]}
    :vehicle/model "318i Touring"
    :vehicle/vin "WBA11FY050FW26985"
    :vehicle/weight 1739}])

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
                   [:fahrzeug/parking-spot {}]])

(defq get-transports [stem] (::transports stem))

(defq get-filters [stem]
  (let [transports (get-transports stem)]
    (into {:fahrzeug/priorities [:checkbox "🤝"]}
          (map (fn [[k opts]] [k [:omnibox
                                  (into #{} (map get-in transports (repeat (->v k))))
                                  opts]])
               base-filters))))

(defq get-header-fields [stem] (::header-fields stem))

(defq get-choices [stem id]
  (-> stem get-filters (get-in [id 1])))

(defn init-state [state]
  (merge
   state
   {::transports    transports
    ::header-fields [[:transport/destination :address/city]
                     [:transport/destination :address/postcode]]}))
