(ns nextjournal.offworld
  (:require
   [clojure.walk :as walk]
   [datastar :as-alias 🚀]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.baseline :as-alias k]
   [nextjournal.offworld.offline :as 🌠]
   [nextjournal.offworld.util :as ou]
   #?@(:cljs
       [[nexus.core :as nexus]
        [replicant.dom :as rdom]]))
  #?(:cljs (:require-macros [nextjournal.offworld])))

#?(:cljs (def ^:dynamic client-nexus-static {}))
#?(:cljs (def ^:dynamic client-nexus-registry {}))
#?(:cljs (def ^:dynamic server-nexus-static {})
   :clj  (def server-nexus-static (atom {})))
#?(:cljs (def ^:dynamic server-nexus-registry {})
   :clj  (def server-nexus-registry (atom {})))

#?(:cljs (defonce memories (js/WeakMap.)))

#?(:cljs (def online? 🌠/!online?))

(def mode (atom :csr))

#?(:cljs (defn recall [node]
           (case @mode
             :csr (rdom/recall node)
             :ssr (.get memories node))))

(defn dissoc-handlers [nexus k]
  (let [dissoc-meta #(into {} (remove (comp k meta val)) %)]
    (-> nexus
        (update :nexus/actions dissoc-meta)
        (update :nexus/effects dissoc-meta)
        (update :nexus/placeholders dissoc-meta))))

#?(:cljs
   (defn get-client-nexus [& {render-mode :mode :or {render-mode @mode}}]
     (case render-mode
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

(defn client-action? [client-nexus [k :as action]]
  (and (not (::🪐/server (meta action)))
       (contains? (:nexus/actions client-nexus {}) k)))

(defn client-effect? [client-nexus [k :as action]]
  (and (not (::🪐/server (meta action)))
       (contains? (:nexus/effects client-nexus {}) k)))

(defn server-action? [server-nexus [k :as action]]
  (and (not (::🪐/client (meta action)))
       (contains? (:nexus/actions server-nexus {}) k)))

(defn server-effect? [server-nexus [k :as action]]
  (and (not (::🪐/client (meta action)))
       (contains? (:nexus/effects server-nexus {}) k)))

#?(:cljs
   (defn build-event-map [e]
     (let [node  (.-target e)]
       (cond-> {:replicant/trigger :replicant.trigger/dom-event
                :replicant/dom-event e}
         node (assoc :replicant/node node)))))

#?(:cljs
   (defn build-lifecycle-map [node]
     {:replicant/life-cycle :replicant.life-cycle/mount
      :replicant/node       node
      :replicant/remember   (fn remember [memory]
                              (.set ^js memories node memory))}))

#?(:cljs
   (defn divert
     ([trigger js-data actions-str]
      (divert (get-client-nexus) (get-server-nexus) trigger js-data actions-str))
     ([client-nexus server-nexus trigger js-data actions-str]
      (let [actions           (ou/deserialize actions-str)
            dispatch-data     (case trigger
                                "event"     (build-event-map js-data)
                                "lifecycle" (build-lifecycle-map js-data))
            client-actions    (filterv #(or (client-action? client-nexus %)
                                            (client-effect? client-nexus %)) actions)
            server-actions    (filterv #(or (server-action? server-nexus %)
                                            (server-effect? server-nexus %)) actions)
            {:keys [effects]} (nexus/expand-actions client-nexus nil client-actions dispatch-data)
            client-effects    (filterv #(client-effect? client-nexus %) effects)
            server-effects    (filterv #(server-effect? server-nexus %) effects)
            bad-actions       (filterv #(and (server-action? server-nexus %)
                                             (not (server-effect? server-nexus %)))
                                       effects)
            actions-to-send   (seq (concat server-effects server-actions))]
        (when (seq bad-actions)
          (js/console.warn "🪐OFFWORLD: These keys were returned from an action handler: "
                           (pr-str (mapv first bad-actions))
                           "They're listed in the nexus as actions, not effects."
                           "In SSR mode, they won't be sent to the server (or executed at all)."
                           "This matches the behavior of CSR mode. By design, actions can't trigger actions."))
        (when online?
          (nexus/dispatch client-nexus (atom {}) dispatch-data client-effects)
          (ou/serialize (nexus/interpolate client-nexus dispatch-data (vec actions-to-send))))))))

(defn offline? [stem]
  #?(:clj false
     :cljs (::offline? (meta stem))))

#?(:cljs
   (defn offline-dispatch [dispatch-data actions]
     (let [client-nexus      (get-client-nexus)
           server-nexus      (get-server-nexus)
           client-actions    (filterv #(or (client-action? client-nexus %)
                                           (client-effect? client-nexus %)) actions)
           server-actions    (filterv #(or (server-action? server-nexus %)
                                           (server-effect? server-nexus %)) actions)
           {:keys [effects]} (nexus/expand-actions client-nexus nil client-actions dispatch-data)
           server-effects    (filterv #(server-effect? server-nexus %) effects)
           actions-to-log    (seq (concat server-effects server-actions))]
       (swap! 🌠/!action-log (fnil into []) actions-to-log)
       (nexus/dispatch (get-client-nexus {:mode :csr}) 🌠/!system dispatch-data actions))))

(defn d*-dispatch [actions]
  (str "@get('/replicant-dispatch', {payload: {actions: "
       "nextjournal.offworld.divert("
       "'event',"
       "evt, '"
       (ou/serialize actions)
       "')}})"))

(defn d*-dispatch-init [actions signal-name]
  (str "@get('/replicant-dispatch', {payload: {actions: "
       "nextjournal.offworld.divert("
       "'lifecycle',"
       "$" signal-name ", '"
       (ou/serialize actions)
       "')}})"))

(defn attr->d*
  "Converts top-level hiccup attributes to datastar expressions.
  Returns a sorted-map, since datastar depends on some keys appearing
  earlier in the attributes."
  [{:as m ::🚀/keys [data-init]}]
  (let [signal-name "offworld-ref"]
    (if-not data-init
      m
      (into (ou/priority-sorted-map [:data-ref])
            (merge m
                   {:data-ref  signal-name
                    :data-init (d*-dispatch-init data-init signal-name)})))))

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

#?(:clj
   (defmacro defc
     {:clj-kondo/lint-as 'clojure.core/defn}
     [sym & decls]
     (let [k (str (ns-name *ns*) "/" sym)]
       `(do
          (k/defq ~sym ~@decls)
          (swap! ou/registry assoc-in [:render-fn ~k] #?(:clj  (var ~sym)
                                                         :cljs ~sym))
            #?(:clj  (var ~sym)
               :cljs ~sym)))))

(comment
  (require '[nextjournal.baseline :as k])

  (k/defq a [stem] true)

  (k/defq b {::k/deps `a} [stem] (when (a stem) (:b stem)))

  (defc render-fn {::k/deps `b} [stem] (b stem))

  (k/trace (render-fn {})))
