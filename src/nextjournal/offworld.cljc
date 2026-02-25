(ns nextjournal.offworld
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [datastar :as-alias 🚀]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.baseline :as-alias k]
   #?@(:cljs
       [[replicant.core :as replicant]
        [nexus.core :as nexus]])))

#?(:cljs (def ^:dynamic client-nexus-static {}))
#?(:cljs (def ^:dynamic client-nexus-registry {}))
#?(:cljs (def ^:dynamic server-nexus-static {})
   :clj  (def server-nexus-static (atom {})))
#?(:cljs (def ^:dynamic server-nexus-registry {})
   :clj  (def server-nexus-registry (atom {})))

(def mode (atom :csr))

(defn dissoc-handlers [nexus k]
  (let [dissoc-meta #(into {} (remove (comp k meta val)) %)]
    (-> nexus
        (update :nexus/actions dissoc-meta)
        (update :nexus/effects dissoc-meta)
        (update :nexus/placeholders dissoc-meta))))

#?(:cljs
   (defn get-client-nexus []
     (case @mode
       :csr {:nexus/system->state (some :nexus/system->state [client-nexus-static
                                                              client-nexus-registry])
             :nexus/actions       (merge (:nexus/actions client-nexus-static)
                                         (:nexus/actions client-nexus-registry)
                                         (:nexus/actions server-nexus-static)
                                         (:nexus/actions server-nexus-registry))
             :nexus/effects       (merge (:nexus/effects client-nexus-static)
                                         (:nexus/effects client-nexus-registry)
                                         (:nexus/effects server-nexus-static)
                                         (:nexus/effects server-nexus-registry))
             :nexus/placeholders  (merge (:nexus/placeholders client-nexus-static)
                                         (:nexus/placeholders client-nexus-registry)
                                         (:nexus/placeholders server-nexus-static)
                                         (:nexus/placeholders server-nexus-registry))
             :nexus/interceptors  ((fnil into [])
                                   (:nexus/placeholders client-nexus-static)
                                   (:nexus/interceptors client-nexus-registry))}
       :ssr (-> (merge-with merge client-nexus-static client-nexus-registry)
                (dissoc-handlers ::🪐/server)))))

#?(:cljs
   (defn get-server-nexus []
     (case @mode
       :csr nil
       :ssr (-> (merge-with merge server-nexus-static server-nexus-registry)
                (dissoc-handlers ::🪐/client)))))

#?(:cljs
   (defn register-client-nexus! [client-nexus & [registry]]
     (set! nextjournal.offworld/client-nexus-registry registry)
     (set! nextjournal.offworld/client-nexus-static client-nexus)
     (get-client-nexus)))

#?(:cljs
   (defn register-server-nexus! [server-nexus & [registry]]
     (set! nextjournal.offworld/server-nexus-registry registry)
     (set! nextjournal.offworld/server-nexus-static server-nexus)
     (get-server-nexus)))

(defn serialize [actions]
  (binding [*print-meta* true]
  (-> (pr-str actions)
      (str/replace  "\"" "%20"))))

(defn deserialize [s]
  (-> s
      (str/replace  "%20" "\"")
      edn/read-string))

(defn client-action? [client-nexus [k :as action]]
  (and (not (::🪐/server (meta action)))
       ((merge (:nexus/actions client-nexus {})
               (:nexus/effects client-nexus {}))
        k)))

(defn server-action? [server-nexus [k :as action]]
  (and (not (::🪐/client (meta action)))
       ((merge (:nexus/actions server-nexus {})
               (:nexus/effects server-nexus {}))
        k)))

#?(:cljs
   (defn divert
     ([dom-event actions-str]
      (divert (get-client-nexus) (get-server-nexus) dom-event actions-str))
     ([client-nexus server-nexus dom-event actions-str]
      (let [actions           (deserialize actions-str)
            dispatch-data     (when dom-event (replicant/build-event-map dom-event))
            client-actions    (filterv #(client-action? client-nexus %) actions)
            server-actions    (filterv #(server-action? server-nexus %) actions)
            {:keys [effects]} (nexus/expand-actions client-nexus nil client-actions dispatch-data)
            client-effects    (filterv #(client-action? client-nexus %) effects)
            server-effects    (filterv #(server-action? server-nexus %) effects)
            actions-to-send   (seq (concat server-effects server-actions))]
        (nexus/dispatch client-nexus (atom {}) dispatch-data client-effects)
        (serialize (nexus/interpolate client-nexus dispatch-data (vec actions-to-send)))))))

(defn d*-dispatch [actions]
  (str "@get('/replicant-dispatch', {payload: {actions: "
       "nextjournal.offworld.divert("
       "evt, '"
       (serialize actions)
       "')}})"))

(defn attr->d*
  "Converts top-level hiccup attributes to datastar expressions."
  [{:as m ::🚀/keys [data-init]}]
  (merge m
         (when data-init {:data-init (d*-dispatch data-init)})))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [m]
  (into (dissoc m :on)
        (for [[k v] (:on m)
              :let  [{:datastar/keys [modifiers]} (meta v)]]
          [(keyword (apply str "data-on" k (interleave (repeat "__")
                                                       (map name modifiers))))
           (d*-dispatch v)])))

(defn replicant->d* [hiccup]
  (walk/postwalk
   #(cond-> % (map? %) (-> on-hooks-replicant->d*
                           attr->d*))
   hiccup))
