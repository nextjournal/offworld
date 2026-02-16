(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [nextjournal.table.nexus :as table.nexus]
   [cheshire.core :as cheshire]
   [nextjournal.table.ui :as ui]
   [replicant.string :as rstr]
   [reitit.ring :as ring]
   [ring.util.codec :as codec]
   [nextjournal.table.util :as u]
   [ring.middleware.resource :as resource]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [ring.core.protocols :refer [StreamableResponseBody]]
   [nextjournal.offworld :as 🪐])
  (:import
   (clojure.core.async.impl.channels ManyToManyChannel)))

(def system (atom (u/init-state)))

(def nexus+registry (merge-with merge table.nexus/server (nxr/get-registry)))

(defn dispatch! [actions dispatch-data]
  (nexus/dispatch nexus+registry system dispatch-data actions))

(defn sse-message [{:keys [event lines]}]
  (str "event: " event "\n"
       (str/join "\n"
                 (for [[k v] lines]
                   (str "data: " k " " v)))
       "\n\n"))

(defn channel->output-stream [channel output-stream]
  (with-open [out    output-stream
              writer (io/writer out)]
    (loop []
      (when-let [^String msg (a/<!! channel)]
        (doto writer (.write msg) (.flush))
        (recur)))))

(extend-type ManyToManyChannel
  StreamableResponseBody
  (write-body-to-stream [ch _response output-stream]
    (channel->output-stream ch output-stream)))

(def sse-chan (a/chan))

(add-watch
 system
 ::ui/render
 (fn [_ _ _ new-state]
   (a/put!
    sse-chan
    (sse-message
     {:event "datastar-patch-elements"
      :lines [["elements" (rstr/render (🪐/replicant->d* (ui/render new-state)))]]}))))

(defn sse-handler [_]
  {:status  200
   :headers {"Content-Type"  "text/event-stream"
             "Cache-Control" "no-cache, no-store"}
   :body    sse-chan})

(def datastar-script
  (str "<script type=\"module\" "
       "src=\"https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js\""
       "></script>"))

(defn index-handler [req]
  (let [ssr?        (some-> req :query-string (str/includes? "ssr=true"))
        main-el-rx  #"<main id=\"app\" class=\"p-4\">Loading...</main>"]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body
     (cond-> (slurp "resources/public/index.html")
       ssr? (str/replace #"</head>" (str datastar-script "\n</head>"))
       ssr? (str/replace main-el-rx (rstr/render (🪐/replicant->d* (ui/render @system))))
       ssr? (str/replace
             #"<body>"
             "<body data-init=\"@get('session')\" data-on-signal-patch=\"nextjournal.offworld.update_queries(patch)\">"))}))

(defn read-dispatch [req]
  (some-> req
          :query-string
          codec/form-decode
          (get "datastar")
          cheshire/parse-string
          walk/keywordize-keys
          (update :actions 🪐/deserialize)))

(def handler
  (resource/wrap-resource
   (ring/ring-handler
    (ring/router
     [["/" {:get {:handler index-handler}}]
      ["/session" {:get {:handler sse-handler}}]
      ["/replicant-dispatch"
       {:get {:handler (fn [req]
                         (let [{:keys [actions dispatch_data]}
                               (read-dispatch req)]
                           (dispatch! actions dispatch_data))
                         {:status 200})}}]]))
   "public"))

(comment
  (a/close! sse-chan))
