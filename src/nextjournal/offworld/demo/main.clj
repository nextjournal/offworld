(ns nextjournal.offworld.demo.main
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [nexus.core :as nexus]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
   [starfederation.datastar.clojure.brotli :as brotli]
   [nexus.registry :as nxr]
   [nextjournal.offworld.demo.nexus :as demo.nexus]
   [nextjournal.offworld.demo.ui :as ui]
   [replicant.string :as rstr]
   [nextjournal.offworld.demo.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]
   [nextjournal.offworld.util :as ou]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo :as demo])
  (:import (java.nio.file Files)))

(def system (atom (demo/init-state {})))

(def nexus+registry
  (merge-with merge demo.nexus/server (nxr/get-registry)))

(defn dispatch! [actions]
  (nexus/dispatch nexus+registry system {} actions))

(def common-head
  '([:script {:src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"}]
    [:script {:defer true :src "https://unpkg.com/maplibre-gl@latest/dist/maplibre-gl.js"}]
    [:link {:rel "stylesheet" :href "https://unpkg.com/maplibre-gl@latest/dist/maplibre-gl.css"}]))

(def datastar-script
  [:script {:type "module"
            :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js"}])

(def !connections (atom #{}))

(defn sse-handler [req]
  (hk-gen/->sse-response
   req
   {hk-gen/write-profile (brotli/->brotli-profile)
    hk-gen/on-open
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

(defn offworld-dispatch-handler [req]
  (dispatch! (:actions (ou/read-dispatch req)))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "ok"})

(def csr-script   [:script {:type "module" :src "/js/csr.js"}])
(def csr-prefetch [:link {:rel "prefetch" :href "/js/csr.js"}])

(defn index-page [csr?]
  (str "<!DOCTYPE html>"
       (rstr/render
        [:html {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title "Table"]
          common-head
          (if csr? csr-script (list datastar-script csr-prefetch))]
         (if csr?
           [:body [:main {:id "app"}]]
           [:body {:data-init "@get('session')"}
            (-> @system k/init-state ui/render 🪐/replicant->d*)
            [:script {:src "/js/ssr.js"}]])])))

(defn index-handler [req]
  (let [csr? (some-> req :query-string (str/includes? "csr"))]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (index-page csr?)}))

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
  (case uri
    "/"                  (index-handler req)
    "/offworld-dispatch" (offworld-dispatch-handler req)
    "/offworld-go-online" (offworld-go-online-handler req)
    "/session"           (sse-handler req)
    (if (re-matches #".*\.(js|js\.map|png|svg|css|woff2?)$" uri)
      (serve-file uri (str "resources/public" uri))
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "Not found"})))
