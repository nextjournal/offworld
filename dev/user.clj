(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]))


(defn start-shadow! [opts]
  (shadow-server/start!)
  (shadow/watch :table))

