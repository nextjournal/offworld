(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [clojure.core.async :as a]
   [clojure.walk :as walk]
   [nexus.core :as nexus]
   nextjournal.table.nexus
   [cheshire.core :as cheshire]
   [nextjournal.table.ui :as ui]
   [replicant.string :as rstr]
   [reitit.ring :as ring]
   [ring.util.codec :as codec]
   [clojure.edn :as edn]
   [nextjournal.table.util :as u]
   [ring.middleware.resource :as resource]
   [nextjournal.table.ui.nested-grid :as-alias ng]
   [nextjournal.offworld :as 🪐]))

(def !store (atom (u/init-store)))

(defn dispatch! [actions dispatch-data]
  (nexus/dispatch nextjournal.table.nexus/nexus !store dispatch-data actions))

(defn sse-message [{:keys [event data prefix]}]
  (str "event: " event "\ndata: " prefix (when prefix " ") data "\n\n"))

(def sse-chan (a/chan))

(add-watch !store
           ::ui/render
           (fn [_ _ _ new-value]
             (a/put! sse-chan
                     (sse-message {:event "datastar-patch-elements"
                                   :prefix "elements"
                                   :data (rstr/render
                                          (🪐/replicant->d*
                                           (ui/render new-value)))}))))

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
       ssr? (str/replace main-el-rx (rstr/render (🪐/replicant->d* (ui/render @!store))))
       ssr? (str/replace #"<body>" "<body data-init=\"@get('session')\">"))}))

(defn read-dispatch [req]
  (some-> req
          :query-string
          codec/form-decode
          (get "datastar")
          cheshire/parse-string
          walk/keywordize-keys
          (update :actions edn/read-string)))

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
