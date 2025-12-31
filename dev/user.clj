(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk-view]))

(defonce add-datastar-js-include
  (alter-var-root #'clerk-view/include-css+js
                  (fn [include-css+js-orig]
                    (fn [state]
                      (concat (include-css+js-orig state)
                              [[:script {:type "module" :src "http://localhost:8000/js/datastar.js"}]])))))

(defn start-clerk! []
  (clerk/serve! {:port 8001}))

(defn start-shadow! []
  (shadow-server/start!)
  (shadow/watch :app)
  (shadow/watch :portfolio))

(defn start! [opts]
  (start-shadow!)
  (start-clerk!))



