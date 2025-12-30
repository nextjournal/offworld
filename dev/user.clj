(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]
            nextjournal.clerk))

(defn start-shadow! [opts]
  (shadow-server/start!)
  (shadow/watch :app)
  (shadow/watch :portfolio))

(start-shadow! {})

(nextjournal.clerk/serve! {:browse? true})
