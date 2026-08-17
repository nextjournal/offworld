(ns hooks.nextjournal.offworld.nexus.registry
  (:require [clj-kondo.hooks-api :as api]))

(def ^:private bucket->reg-by
  {:nexus/actions      'nexus.registry/register-action!
   :nexus/expansions   'nexus.registry/register-expansion!
   :nexus/effects      'nexus.registry/register-effect!
   :nexus/placeholders 'nexus.registry/register-placeholder!})

(defn- map-node? [n] (= :map (api/tag n)))

(defn- keyword-node?
  "clj-kondo represents keyword literals as `:token` nodes (like symbols), so we
  identify them by their sexpr rather than by `api/tag`."
  [n]
  (and (= :token (api/tag n)) (keyword? (api/sexpr n))))

(defn- mark-bucket
  "Return `sub-map` with each keyword key annotated as a registration via
  `reg-keyword!`. Values pass through untouched so clj-kondo still analyzes the
  handler fns normally."
  [sub-map reg-by]
  (if (map-node? sub-map)
    (api/map-node
     (mapcat (fn [[k v]]
               [(if (keyword-node? k) (api/reg-keyword! k reg-by) k) v])
             (partition 2 (:children sub-map))))
    sub-map))

(defn register-many!
  "analyze-call hook for `reg-many`. Rebuilds the config map, marking the keys of
  the :nexus/actions / :nexus/effects / :nexus/placeholders sub-maps."
  [{:keys [node]}]
  (let [[reg-sym config] (:children node)]
    (if (and config (map-node? config))
      (let [new-config
            (api/map-node
             (mapcat (fn [[k v]]
                       (let [reg-by (when (keyword-node? k)
                                      (bucket->reg-by (api/sexpr k)))]
                         [k (if reg-by (mark-bucket v reg-by) v)]))
                     (partition 2 (:children config))))]
        {:node (api/list-node [reg-sym new-config])})
      {:node node})))
