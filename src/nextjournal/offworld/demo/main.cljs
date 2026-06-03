(ns nextjournal.offworld.demo.main
  (:require
   [core.lite :as 🪶]
   [clojure.walk :as walk]
   #_[clojure.string :as str]
   #_[nexus.core :as nexus]
   #_[nexus.registry :as nxr]
   [nextjournal.offworld.demo.nexus :as demo.nexus]
   [nextjournal.offworld.demo.ui :as ui]
   [nextjournal.offworld :as 🪐]
   [nexus.registry :as nxr]
   [nextjournal.offworld.util :as ou]
   #_[nextjournal.offworld.demo.offline :as 🌠]
   #_[nextjournal.baseline :as k]
   [nextjournal.offworld.demo :as demo]))

(defonce system
  (atom (demo/init-state {})))

#_(r/set-dispatch!
   (fn [dispatch-data actions]
     (if @🪐/online?
       (nexus/dispatch (🪐/get-client-nexus) system dispatch-data actions)
       (🌠/offline-dispatch dispatch-data actions))))

(nxr/register-system->state! deref)

(nxr/register-effect! :save ^:nexus/batch
  (fn [_ system path-vs]
    (swap! system
           (fn [state]
             (reduce (fn [acc [path v]]
                       (🪶/assoc-in acc path v))
                     state path-vs)))))

(def store (atom {}))

(nxr/dispatch store {} [[:hi]])

(js/console.log (nxr/get-registry))

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! system update :dev/load inc))

(defn main []
  (🪐/set-ux! (if (.includes js/document.location.search "?ssr=true") :ssr :csr))
  (when (= :csr (🪐/get-ux))
    (add-watch system
               ::render
               (fn [_ _ _ new-state]
                #_ (r/render root-el (ui/render (k/init-state new-state)))))
    #_(after-load)))
