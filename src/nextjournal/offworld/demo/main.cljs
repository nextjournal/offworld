(ns nextjournal.offworld.demo.main
  (:require
   [nextjournal.offworld :as 🪐]
   nextjournal.offworld.demo.nexus
   nextjournal.offworld.demo.ui.holiday
   nextjournal.offworld.demo.ui.nested-grid
   nextjournal.offworld.demo.mapbox))

(defn main []
  (🪐/set-ux! (if (.includes js/document.location.search "?ssr=true") :ssr :csr))
  (when (= :csr (🪐/get-ux))
    (let [s (js/document.createElement "script")]
      (set! (.-src s) "/js/csr.js")
      (set! (.-type s) "module")
      (.appendChild js/document.head s))))
