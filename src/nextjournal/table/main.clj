(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [clojure.core.async :as a]
   [clojure.walk :as walk]
   [nexus.core :as nexus]
   [cheshire.core :as cheshire]
   [nextjournal.table.ui :as ui]
   [replicant.string :as rstr]
   [reitit.ring :as ring]
   [ring.util.codec :as codec]
   [clojure.edn :as edn]
   [nextjournal.table.util :as u]
   [ring.middleware.resource :as resource]))

(defonce !store (atom {}))

(def nexus
  {:nexus/system->state deref
   :nexus/effects       {:effects/save (fn save [_ store path value]
                                         (swap! store assoc-in path value))}
   :nexus/actions       {:actions/inc  (fn inc [state path]
                                         [[:effects/save path (+ (:step state) (get-in state path))]])}
   :nexus/placeholders  {:event.target/value :value
                         :fmt/as-long        (fn fmt-as-long [_ value]
                                               (or (some-> value parse-long) 0))
                         :fmt/as-double      (fn fmt-as-double [_ value]
                                               (or (some-> value parse-double) 0.0))}})

(defn dispatch! [actions dispatch-data]
  (nexus/dispatch nexus !store dispatch-data actions))

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
                                          (u/replicant->d*
                                           (ui/render new-value)))}))))

(defn sse-handler [_]
  {:status  200
   :headers {"Content-Type"  "text/event-stream"
             "Cache-Control" "no-cache, no-store"}
   :body    sse-chan})

(defn index-handler [req]
  (let [ssr?        (some-> req :query-string (str/includes? "ssr=true"))
        body-tag-rx #"<body>"
        main-el-rx  #"<main id=\"app\" class=\"p-4\">Loading...</main>"
        html        (cond-> (ui/render @!store)
                      ssr? u/replicant->d*
                      :do  rstr/render)]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body
     (cond-> (slurp "resources/public/index.html")
       :do  (str/replace main-el-rx html)
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
