(ns nextjournal.offworld
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]
   #?@(:cljs
       [[replicant.core :as replicant]
        [nexus.core :as nexus]
        [nextjournal.offworld :as-alias 🪐]])))

#?(:cljs (def ^:dynamic user-nexus {}))

#?(:cljs (def register-nexus! #(set! user-nexus %)))

(defn serialize [actions]
  (-> (pr-str actions)
      (str/replace  "\"" "%20")))

(defn deserialize [s]
  (-> s
      (str/replace  "%20" "\"")
      edn/read-string))

#?(:cljs
   (defn divert
     ([dom-event actions-str]
      (divert user-nexus dom-event actions-str))
     ([nexus dom-event actions-str]
      (let [actions        (deserialize actions-str)
            dispatch-data  (replicant/build-event-map dom-event)
            select-client  #(into {} (filter (comp ::🪐/client meta val)) %)
            client-action? (select-client
                            (merge (:nexus/effects nexus)
                                   (:nexus/actions nexus)))
            client-nexus   (update nexus :nexus/placeholders select-client)
            server-actions (vec (remove (comp client-action? first) actions))
            client-actions (vec (filter (comp client-action? first) actions))]
        (nexus/dispatch client-nexus (atom {}) dispatch-data client-actions)
        (serialize (nexus/interpolate client-nexus dispatch-data server-actions))))))

(defn d*-dispatch [actions]
  (str "@get('/replicant-dispatch', {payload: {actions: "
       "nextjournal.offworld.divert("
       "evt, '"
       (serialize actions)
       "')}})"))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [props]
  (into (dissoc props :on)
        (for [[k v] (:on props)
              :let  [{:datastar/keys [modifiers]} (meta v)]]
          [(keyword (apply str "data-on" k (interleave (repeat "__")
                                                       (map name modifiers))))
           (d*-dispatch v)])))

(defn replicant->d* [hiccup]
  (walk/postwalk
   #(cond-> % (map? %) on-hooks-replicant->d*)
   hiccup))
