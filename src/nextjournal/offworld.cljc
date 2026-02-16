(ns nextjournal.offworld
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.baseline :as-alias k]
   #?@(:cljs
       [[replicant.core :as replicant]
        [nexus.core :as nexus]])))

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
      (let [actions                (deserialize actions-str)
            dispatch-data          (replicant/build-event-map dom-event)
            select-client          #(into {} (filter (comp ::🪐/client meta val)) %)
            client-action?         (select-client
                                    (merge (:nexus/effects nexus)
                                           (:nexus/actions nexus)))
            client-nexus           (update nexus :nexus/placeholders select-client)
            server-actions         (vec (remove (comp client-action? first) actions))
            client-actions         (vec (filter (comp client-action? first) actions))
            {all-effects :effects} (nexus/expand-actions nexus nil client-actions dispatch-data)
            client-effects         (vec (remove (comp ::🪐/server meta) all-effects))
            server-effects         (vec (filter (comp ::🪐/server meta) all-effects))] ;; TODO: insert these in their original placements?
        (nexus/dispatch client-nexus (atom {}) dispatch-data client-effects)
        (serialize (nexus/interpolate client-nexus dispatch-data ((fnil into []) server-actions server-effects)))))))

(def query-placeholders (atom {}))
(def query-results (atom {}))

#?(:cljs
   (defn update-queries [patch]
     (let [q->v (deserialize (get (js->clj patch) "queries"))]
       (swap! query-results merge q->v))))

(nxr/register-placeholder!
 ::k/q
 ^::🪐/client
 (fn [_ k & [opts]]
   (get @query-results (into [::k/q k] opts))))

(defn d*-dispatch [actions]
  (str "@get('/replicant-dispatch', {payload: {actions: "
       "nextjournal.offworld.divert("
       "evt, '"
       (serialize actions)
       "')}})"))

(defn find-query-placeholders [v]
  (let [!acc (atom #{})]
    (clojure.walk/postwalk
     #(do (when (and (vector? %) (= ::k/q (first %)))
            (swap! !acc conj %))
          %)
     v)
    @!acc))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [props !acc]
  (into (dissoc props :on)
        (for [[k v] (:on props)
              :let  [{:datastar/keys [modifiers]} (meta v)
                     _ (swap! !acc into (find-query-placeholders v))]]
          [(keyword (apply str "data-on" k (interleave (repeat "__")
                                                       (map name modifiers))))
           (d*-dispatch v)])))

(defn replicant->d* [hiccup]
  (let [!acc (atom #{})
        res  (walk/postwalk
              #(cond-> % (map? %) (on-hooks-replicant->d* !acc))
              hiccup)]
    (with-meta res {::k/query-placeholders @!acc})))
