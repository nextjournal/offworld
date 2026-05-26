(ns nextjournal.offworld.demo.main
  (:require
   [clojure.walk :as walk]
   #_[clojure.string :as str]
   #_[nexus.core :as nexus]
   #_[nexus.registry :as nxr]
   [nextjournal.offworld.demo.nexus :as demo.nexus]
   #_[nextjournal.offworld.demo.ui :as ui]
   [nextjournal.offworld :as 🪐]
   [nexus.registry :as nxr]
   #_[nextjournal.offworld.demo.offline :as 🌠]
   #_[nextjournal.baseline :as k]
#_   [nextjournal.offworld.demo :as demo]))

#_(defonce system
    (atom (demo/init-state {})))

#_(js/console.log (walk/postwalk inc [1 2 3 [1 2 3]]))


#_(defn- postwalk [f x]
  (f (cond
       (vector? x) (mapv #(postwalk f %) x)
       (map? x)    (reduce-kv (fn [m k v] (assoc m k (postwalk f v))) {} x)
       :else x)))

#_(assoc {} 1 2)
#_(into {} [[1 2]])

#_(js/console.log (map inc [ 1 2 3]))
#_(🪐/register-client-nexus! demo.nexus/client {})
#_(🪐/register-server-nexus! demo.nexus/server {} #_(nxr/get-registry))

#_(🪐/get-client-nexus)

#_(r/set-dispatch!
   (fn [dispatch-data actions]
     (if @🪐/online?
       (nexus/dispatch (🪐/get-client-nexus) system dispatch-data actions)
       (🌠/offline-dispatch dispatch-data actions))))

#_(defonce root-el
  (js/document.getElementById "app"))

#_(defn ^:dev/after-load after-load []
  (swap! system update :dev/load inc))

#_(defn main []
  (reset! 🪐/mode :ssr #_(if (str/includes? js/document.location.search "?ssr=true") :ssr :csr))
  #_(when (= :csr @🪐/mode)
    (add-watch system
               ::render
               (fn [_ _ _ new-state]
                 (r/render root-el (ui/render (k/init-state new-state)))))
    (after-load)))

#_(nxr/register-action! :x (fn [] (js/console.log "hi")))


#_(defn- postwalk [f x]
  (f (cond
       (vector? x) (mapv #(postwalk f %) x)
       (map? x)
       (when-let [s (.-strobj ^js x)]
         (let [t #js {}]
           (.forEach (js/Object.keys s) #(aset t % (postwalk f (aget s %))))
           (set! (.-strobj ^js x) t)
           x))
       :else x)))

#_(js/console.log (postwalk identity [1 2 3 [1 2 3 {:x 1}]]))

  (defn- postwalk [f x]
    (f (cond
         (vector? x) (mapv #(postwalk f %) x)
         (map? x)
         (when-let [s (.-strobj ^js x)]
           (let [t #js {}]
             (.forEach (js/Object.keys s) #(aset t % (postwalk f (aget s %))))
             (set! (.-strobj ^js x) t)
             x))
         :else x)))

;; simulate interpolate-1: replace [:upper s] placeholder with (str/upper-case s)
(let [placeholders {:upper (fn [_ s] (.toUpperCase s))}
      action       [:my-action {:label [:upper "hello"]} [:upper "world"]]]
  (postwalk (fn [x]
              (if-let [f (when (vector? x) (get placeholders (first x)))]
                (apply f nil (rest x))
                x))
            action))

(defn main [])
