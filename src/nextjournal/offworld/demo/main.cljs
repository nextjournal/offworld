(ns nextjournal.offworld.demo.main
  (:require
   [nextjournal.offworld :as 🪐]
   nextjournal.offworld.demo.nexus
   nextjournal.offworld.demo.ui.holiday
   nextjournal.offworld.demo.ui.nested-grid))

(defn handoff! []
  (let [s (js/document.createElement "script")]
    (set! (.-src s) "/js/csr.js")
    (set! (.-type s) "module")
    (.appendChild js/document.head s)))

(defn start-handoff! []
  (when-let [link (js/document.querySelector "link[rel='prefetch'][href='/js/csr.js']")]
    (.addEventListener link "load" handoff!)))

(defn main []
  (let [params (js/URLSearchParams. js/document.location.search)]
    (🪐/set-ux! (if (.has params "csr") :csr :ssr))
    (when-not js/goog.DEBUG (start-handoff!))))
