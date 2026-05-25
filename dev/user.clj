(ns user
  (:require
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as shadow-server]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.view :as clerk-view]
   [ring.adapter.jetty :refer [run-jetty]]
   [org.httpkit.server :as http]
   [nextjournal.offworld.demo.main :as main]))

(defonce add-datastar-js-include
  (alter-var-root
   #'clerk-view/include-css+js
   (fn [include-css+js-orig]
     (fn [state]
       (concat (include-css+js-orig state)
               [[:script {:type "module"
                          :src  "http://localhost:8000/datastar.js"}]])))))

(defn start-clerk! []
  ;; keep clerk start available on a different port if needed
  (clerk/serve! {:port 9001}))

(defn start-http-kit! []
  (http/run-server #'main/handler {:port 8000})
  #_(run-jetty #'main/handler {:port  8000
                             :join? false
                             :host  "localhost"}))

(defn start-shadow! []
  (shadow-server/start!)
  (shadow/watch :app))

(defn start! [& [_opts]]
  (start-shadow!)
  (start-http-kit!)
  (start-clerk!))
