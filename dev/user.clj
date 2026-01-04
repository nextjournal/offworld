(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk-view]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as res]))

(defonce add-datastar-js-include
  (alter-var-root #'clerk-view/include-css+js
                  (fn [include-css+js-orig]
                    (fn [state]
                      (concat (include-css+js-orig state)
                              [[:script {:type "module" :src "http://localhost:8000/js/datastar.js"}]])))))

(defn start-clerk! []
  ;; keep clerk start available on a different port if needed
  (clerk/serve! {:port 9001}))

(def not-found {:status 404 :body "Not found"})

(defn handler [{:keys [uri] :or {uri "/"}}]
  (def req req)
  (let [path      (if (= "/" uri) "index.html" (subs uri 1))
        resp      (res/file-response path {:root "public"})]
    (or resp not-found)))

(defn start-ring! []
  (run-jetty #'handler {:port  8000
                        :join? false
                        :host  "localhost"}))

(defn start-shadow! []
  (shadow-server/start!)
  (shadow/watch :app)
  (shadow/watch :portfolio))

(defn start! [_opts]
  (start-shadow!)
  (start-ring!)
  (start-clerk!))


