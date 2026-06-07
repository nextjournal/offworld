(ns nextjournal.offworld.demo.main
  (:require
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.demo.offline :as 🌠]
   nextjournal.offworld.demo.nexus
   nextjournal.offworld.demo.ui.holiday
   nextjournal.offworld.demo.ui.nested-grid))

(defn main []
  (let [params (js/URLSearchParams. js/document.location.search)]
    (js/console.log "SSR BUNDLE STARTING")
    (🪐/set-ux! (if (.has params "csr") :csr :ssr))
    (when (.-serviceWorker js/navigator)
      (.register (.-serviceWorker js/navigator) "/sw.js"))))
