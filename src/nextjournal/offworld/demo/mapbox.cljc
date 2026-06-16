(ns nextjournal.offworld.demo.mapbox
  (:require
   [nextjournal.offworld :as 🪐]
   [nexus.registry :as nxr]
   [nextjournal.offworld.stem :as 🌿]
   [datastar :as-alias 🚀])
  #?(:cljs
     (:require-global [maplibregl.Map :as Map])))

#?(:cljs
   (nxr/register-effect! ::init ^::🪐/client
     (fn init
       ([ctx _ id] (init ctx _ id {}))
       ([ctx _ id opts]
        (let [center   (:center opts)
              remember (:replicant/remember (:dispatch-data ctx))]
          (remember
           (Map.
            #js {:container id
                 :style     "https://demotiles.maplibre.org/style.json"
                 :center    (when center #js [(first center) (second center)])
                 :zoom      4})))))))

#?(:cljs
   (nxr/register-effect! ::pan-to ^::🪐/client
     (fn [_ _ id e n]
       (let [^js map-ref (🪐/recall (js/document.getElementById id))]
         (.panTo map-ref #js [e n])))))

(defn mapbox [& {:as      state
                 :keys    [id]
                 ::🌿/keys [path]
                 :or      {id "offworld-mapbox-demo"}}]
  (let [{:keys [center]
         :or   {center [12.4 51.52]}} (🌿/local state)]
  [:div
   [:div {:id                 id
          :data-ignore-morph  true
          :style              {:width "400px" :height "300px"}
          :replicant/on-mount [[::init id {:center center}]]}
    "[mapbox renders here]"]
   [:button {:on {:click [[::pan-to id 4.89 52.38]
                          [:effects/save path {:center [4.89 52.38]}]]}}
    "Amsterdam"]
   [:div.inline " "]
   [:button {:on {:click [[::pan-to id 13.4 52.52]
                          [:effects/save path {:center [13.4 52.52]}]]}}
    "Berlin"]]))
