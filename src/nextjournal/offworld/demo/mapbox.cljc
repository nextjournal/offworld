(ns nextjournal.offworld.demo.mapbox
  (:require
   [nextjournal.offworld :as 🪐]
   [nexus.registry :as nxr]
   [datastar :as-alias 🚀])
  #?(:cljs
     (:require-global [maplibregl.Map :as Map])))

#?(:cljs
   (nxr/register-effect! ::init ^::🪐/client
     (fn [{{:replicant/keys [remember]} :dispatch-data} _ id]
       (remember
        (Map.
         (clj->js {:container id
                   :style     "https://demotiles.maplibre.org/style.json"
                   :center    [12.4 51.52]
                   :zoom      4}))))))

#?(:cljs
   (nxr/register-effect! ::pan-to ^::🪐/client
     (fn [_ _ id e n]
       (let [^js map-ref (🪐/recall (js/document.getElementById id))]
         (.panTo map-ref (clj->js [e n]))))))

(defn mapbox [& {:keys [id]
                 :or   {id "offworld-mapbox-demo"}}]
  [:div
   [:div {:id                 id
          :data-ignore-morph  true
          :style              {:width "400px" :height "300px"}
          ::🚀/data-init      [[::init id]]
          :replicant/on-mount [[::init id]]}
    "[mapbox renders here]"]
   [:button {:on {:click [[::pan-to id 4.89 52.38]]}}
    "Amsterdam"]
   [:div.inline " "]
   [:button {:on {:click [[::pan-to id 13.4 52.52]]}}
    "Berlin"]])
