(ns nextjournal.ductile.load-builder
  (:require
   [clojure.set :as set]
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
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-23T11:19:01.169-00:00"
    :ductile/id #uuid "ce61b764-1783-4332-9ae1-56168661aca2"
    :exit/expected-datetime #inst "2025-12-16T10:51:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-18T10:51:01.832-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "FZ_AUS_LKW/5"
    :load/id "LL-KRE-13619"
    :make/name "BMW"
    :transport/destination {:address/city "Borken"
                            :address/country #:country{:code "DEU"}
                            :address/name "Jungeblut GmbH & Co. KG"
                            :address/postcode "46325"
                            :address/street "Nordring 35-39"
                            :ductile/id #uuid
                                         "e52523ee-7362-415c-acfe-e4f9b170d3e0"
                            :elog/adresse-id "0000143543"
                            :external.bmw/location-codes ["035372"]
                            :location/latitude 51.84874
                            :location/longitude 6.85739
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
    :vehicle/model "X1 M35i xDrive"
    :vehicle/vin "WBA11EF0905552537"
    :vehicle/weight 1797}
   {:contract/name "BMW Niederlassungen"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-22T07:44:57.289-00:00"
    :ductile/id #uuid "d6221a02-4f81-4842-851d-f3f1f865900b"
    :exit/expected-datetime #inst "2025-12-17T11:42:00.000-00:00"
    :fahrzeug/call-off-datetime #inst "2025-12-22T06:43:13.200-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2026-01-11T23:00:00.000-00:00"
    :fahrzeug/parking-spot "A/65/6"
    :load/id nil
    :make/name "BMW"
    :transport/destination
      {:address/city                "Essen"
       :address/country             #:country{:code "DEU"}
       :address/name                "BMW Niederlassung Essen"
       :address/postcode            "45141"
       :address/street              "Berthold-Beitz-Boulevard 508"
       :ductile/id                  #uuid "361cca54-807a-4c96-bb4f-64f55c8a5dc8"
       :elog/adresse-id             "0000143683"
       :external.bmw/location-codes ["000080" "024938"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO - FR. 08:00 - 15:00 Uhr\nKeine Nachtanlieferung!"
       :location/latitude           51.47438
       :location/longitude          7.00111
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
    :vehicle/model "520d Limousine"
    :vehicle/vin "WBA11FL060CX21810"
    :vehicle/weight 1954}
   {:contract/name "Weller Premium"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-22T13:00:38.295-00:00"
    :ductile/id #uuid "b437d39a-9306-43fb-b6eb-615766283205"
    :exit/expected-datetime #inst "2025-12-22T10:54:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-24T10:54:16.119-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/37/13"
    :load/id nil
    :make/name "BMW"
    :transport/destination
      {:address/city                "Gütersloh"
       :address/country             #:country{:code "DEU"}
       :address/name                "WELLER Premium Deutschland GmbH"
       :address/postcode            "33334"
       :address/street              "Hülsbrockstr. 83-87"
       :ductile/id                  #uuid "d89708ef-54ef-4951-a440-81ee344f2a37"
       :elog/adresse-id             "0000159563"
       :external.bmw/location-codes ["029150"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-DO: 08:00 - 16:45 Uhr\nFR   : 08:00 - 16:00 Uhr\nkeine Nachtanlieferung!!"
       :location/latitude           51.91862
       :location/longitude          8.41031
       :location/types              [:location.type/billing
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
    :vehicle/vin "WBA11FY010FW24991"
    :vehicle/weight 1739}
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-22T13:29:47.172-00:00"
    :ductile/id #uuid "c2eb7b0d-7710-411e-bb0c-083b44c98a7b"
    :exit/expected-datetime #inst "2025-12-22T11:45:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-24T11:45:27.489-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/33/25"
    :load/id nil
    :make/name "BMW"
    :transport/destination
      {:address/city                "Moers"
       :address/country             #:country{:code "DEU"}
       :address/name                "Fett & Wirtz Automobile\nGmbH & Co. KG"
       :address/postcode            "47443"
       :address/street              "Bullermannshof 11"
       :ductile/id                  #uuid "92863e7d-a27d-45cf-8486-ed9e2042fc4d"
       :elog/adresse-id             "0000159165"
       :external.bmw/location-codes ["001911" "001911G"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-DO: 08:00 - 16:30 Uhr\nFR:    08:00 - 15:00 Uhr\nKeine Nachtanlieferung!!!"
       :location/latitude           51.46065
       :location/longitude          6.61565
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
    :vehicle/model "318i Touring"
    :vehicle/vin "WBA11FY030FW21882"
    :vehicle/weight 1739}
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-23T10:49:16.148-00:00"
    :ductile/id #uuid "74d365b5-0c2b-425c-bbf8-a3162534d8b5"
    :exit/expected-datetime #inst "2025-12-22T11:47:00.000-00:00"
    :fahrzeug/call-off-datetime #inst "2025-12-22T12:31:42.765-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-23T23:00:00.000-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "FZ_AUS_LKW/6"
    :fahrzeug/priorities [:prioritiy/binding-customer-appointment]
    :load/id "LL-KRE-13617"
    :make/name "BMW"
    :transport/destination
      {:address/city                "Oberhausen"
       :address/country             #:country{:code "DEU"}
       :address/name                "Muhra GmbH\nAutohaus"
       :address/postcode            "46149"
       :address/street              "Im Erlengrund 1"
       :ductile/id                  #uuid "1268f504-f53f-495d-830e-304ce28d3f14"
       :elog/adresse-id             "0000138393"
       :external.bmw/location-codes ["000232"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-FR: 10:00 - 18:00 Uhr\nkeine Nachtanlieferung!!"
       :location/latitude           51.51673
       :location/longitude          6.8196
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
    :vehicle/model "318i Touring"
    :vehicle/vin "WBA11FY030FW21980"
    :vehicle/weight 1739}
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-23T08:13:06.908-00:00"
    :ductile/id #uuid "d87602c0-9559-4e92-8ad1-55f1608dad2f"
    :exit/expected-datetime #inst "2025-12-22T10:02:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-24T10:02:08.487-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "FZ_AUS_LKW/2"
    :load/id "LL-KRE-13606"
    :make/name "BMW"
    :transport/destination
      {:address/city                "Vechta"
       :address/country             #:country{:code "DEU"}
       :address/name                "WELLER Premium Deutschland GmbH"
       :address/postcode            "49377"
       :address/street              "Osloer Str.3"
       :ductile/id                  #uuid "b466a58f-a3d2-4b96-9a02-46413f26823f"
       :elog/adresse-id             "0000159566"
       :external.bmw/location-codes ["040263"]
       :location/delivery-modalities
         "Anlieferzeiten:\nMO-FR: 08:00 - 18:00 Uhr\nSA   : 10:00 - 13:00 Uhr\nkeine Nachtanlieferung!!\n\nEine Umfahrung der Immobilie ist bis auf Weiteres nicht möglich. Der Transporter kann vor dem Grundstück entlang der Straße parken und abladen."
       :location/latitude           52.73086
       :location/longitude          8.26452
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
    :vehicle/model "318i Touring"
    :vehicle/vin "WBA11FY050FW22063"
    :vehicle/weight 1739}
   {:contract/name "BMW Verteilung"
    :customer/name "BMW AG"
    :dispo/available-datetime #inst "2025-12-22T10:49:54.000-00:00"
    :ductile/id #uuid "4ec1bf9a-a89d-4015-b6fa-7a3ad953d361"
    :exit/expected-datetime #inst "2025-12-22T10:18:00.000-00:00"
    :fahrzeug/expected-customer-delivery-datetime
      #inst "2025-12-24T10:18:58.341-00:00"
    :fahrzeug/labels #{:label/quick-turnaround}
    :fahrzeug/parking-spot "A/9/17"
    :load/id nil
    :make/name "BMW"
    :transport/destination {:address/city "Spenge"
                            :address/country #:country{:code "DEU"}
                            :address/name "Autohaus Becker-Tiemann"
                            :address/postcode "32139"
                            :address/street "Düttingdorfer Str. 342"
                            :ductile/id #uuid
                                         "76048c78-00a5-46c9-8bff-347ee7f71165"
                            :elog/adresse-id "0000159810"
                            :external.bmw/location-codes ["052090"]
                            :location/latitude 52.12789
                            :location/longitude 8.41527
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
    :vehicle/model "318i Touring"
    :vehicle/vin "WBA11FY050FW25013"
    :vehicle/weight 1739}
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
