(ns nextjournal.table.main
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [nexus.core :as nexus]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
   [nexus.registry :as nxr]
   [nextjournal.table.nexus :as table.nexus]
   [nextjournal.table.ui :as ui]
   [replicant.string :as rstr]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.util :as ou]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo :as demo]
   [selmer.parser :as selmer]
   [selmer.util])
  (:import (java.nio.file Files)))

(def system (atom (demo/init-state {})))

(def nexus+registry
  (merge-with merge table.nexus/server (nxr/get-registry)))

(defn dispatch! [actions]
  (nexus/dispatch nexus+registry system {} actions))

(def datastar-script
  (str "<script type=\"module\" "
       "src=\"https://cdn.jsdelivr.net/gh"
       "/starfederation/datastar@1.0.0-RC.7"
       "/bundles/datastar.js\""
       "></script>"))

(def !connections (atom #{}))

(defn sse-handler [req]
  (hk-gen/->sse-response req
    {hk-gen/on-open
     (fn [sse-gen]
       (swap! !connections conj sse-gen))

     hk-gen/on-close
     (fn [sse-gen status]
       (swap! !connections disj sse-gen))}))

(defn broadcast-elements! [elements]
  (doseq [c @!connections]
    (d*/patch-elements! c elements)))

(defn sse-message [{:keys [event lines]}]
  (str "event: " event "\n"
       (str/join "\n"
                 (for [[k v] lines]
                   (str "data: " k " " v)))
       "\n\n"))

(add-watch
 system
 ::ui/render
 (fn [_ _ _ new-state]
   (broadcast-elements!
    (sse-message
     {:event "datastar-patch-elements"
      :lines [["elements" (-> new-state
                              k/init-state
                              ui/render
                              🪐/replicant->d*
                              rstr/render)]]}))))

(defn replicant-dispatch-handler [req]
  (dispatch! (:actions (ou/read-dispatch req)))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "ok"})

(defn index-handler [req]
  (let [ssr? (some-> req :query-string (str/includes? "ssr=true"))]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body
     (selmer.util/without-escaping
      (selmer/render-file
       "public/index.html"
       (when ssr?
         {:extra-head datastar-script
          :body-attr  "data-init=\"@get('session')\""
          :main       (-> @system
                          k/init-state
                          ui/render
                          🪐/replicant->d*
                          rstr/render)})))}))

(defn offworld-go-online-handler [req]
  (dispatch! (ou/read-action-log req))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "ok"})

(defn serve-file [uri path]
  (let [file (when (fs/exists? path)
               (cond-> path
                 (fs/directory? path) (fs/file "index.html")))]
    (if (fs/exists? file)
      {:status  200
       :headers (cond-> {"Content-Type" (Files/probeContentType (fs/path file))}
                  (and (= "js" (fs/extension file)) (fs/exists? (str file ".map"))) (assoc "SourceMap" (str uri ".map")))
       :body    (fs/read-all-bytes file)}
      {:status 404})))

(defn handler [{:as req :keys [uri]}]
  (case (:uri req)
    "/"                   (index-handler req)
    "/replicant-dispatch" (replicant-dispatch-handler req)
    "/offworld-go-online" (offworld-go-online-handler req)
    "/session"            (sse-handler req)
    "/js/main.js"         (serve-file uri (str "resources/public" uri))
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "Not found"}))
