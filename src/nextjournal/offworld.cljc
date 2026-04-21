(ns nextjournal.offworld
  (:require
   #?@(:cljs
       [[nexus.core :as nexus]
        [replicant.dom :as rdom]])
   [clojure.string :as str]
   [clojure.walk :as walk]
   [datastar :as-alias 🚀]
   [nextjournal.baseline :as-alias k]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.util :as ou])
  #?(:cljs (:require-macros [nextjournal.offworld])))

(def registry (atom {}))

#?(:cljs (def ^:dynamic client-nexus-static {}))
#?(:cljs (def ^:dynamic client-nexus-registry {}))
#?(:cljs (def ^:dynamic server-nexus-static {})
   :clj  (def server-nexus-static (atom {})))
#?(:cljs (def ^:dynamic server-nexus-registry {})
   :clj  (def server-nexus-registry (atom {})))

#?(:cljs (defonce memories (js/WeakMap.)))

#?(:cljs (def online? (atom true)))

#?(:cljs (defn go-online! [] (reset! online? true)))
#?(:cljs (defn go-offline! [] (reset! online? false)))

(defonce mode (atom :csr))

#?(:cljs (defn recall [node]
           (case @mode
             :csr (rdom/recall node)
             :ssr (.get memories node))))

#?(:cljs (def ^:dynamic serialize-fn ou/serialize))
#?(:cljs (def ^:dynamic deserialize-fn ou/deserialize))

#?(:cljs (defn register-serialize-fn! [f] (set! nextjournal.offworld/serialize-fn f)))
#?(:cljs (defn register-deserialize-fn! [f] (set! nextjournal.offworld/deserialize-fn f)))

(defn dissoc-handlers [nexus k]
  (let [dissoc-meta #(into {} (remove (comp k meta val)) %)]
    (-> nexus
        (update :nexus/actions dissoc-meta)
        (update :nexus/effects dissoc-meta)
        (update :nexus/placeholders dissoc-meta))))

(defn call-or-value [x] (if (fn? x) (x) x))

#?(:cljs
   (defn get-client-nexus [& {render-mode :mode :or {render-mode @mode}}]
     (let [client-nexus-static   (call-or-value client-nexus-static)
           client-nexus-registry (call-or-value client-nexus-registry)
           server-nexus-static   (call-or-value server-nexus-static)
           server-nexus-registry (call-or-value server-nexus-registry)]
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
                  (dissoc-handlers ::🪐/server))))))

#?(:cljs
   (defn get-server-nexus []
     (case @mode
       :csr nil
       :ssr (-> (merge-with merge
                            (call-or-value server-nexus-static)
                            (call-or-value server-nexus-registry))
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

(declare dispatch!)

#?(:cljs
   (defn build-event-map [e {:keys [dispatch-url conn-id] :as payload}]
     (let [node (some-> e .-target)]
       (cond-> {:replicant/trigger   :replicant.trigger/dom-event
                :replicant/dom-event e}
         node (assoc :replicant/node node)))))

#?(:cljs
   (defn build-lifecycle-map [node {:keys [lifecycle dispatch-url conn-id] :as payload}]
     (merge
      {:replicant/life-cycle lifecycle
       :replicant/node       node
       :replicant/remember   (fn remember [memory]
                               (.set ^js memories node memory))
       ::🪐/dispatch         (fn [actions]
                               (dispatch! dispatch-url actions (merge payload
                                                                      {:event         node
                                                                       :trigger       :lifecycle
                                                                       :extra-payload {:conn-id conn-id}})))}
      (when (not= lifecycle :replicant/mount)
        {:replicant/memory (recall node)}))))

#?(:cljs
   (defn divert* [{:keys [actions trigger lifecycle dispatch-url] :as payload} js-data]
     (let [server-nexus      (get-server-nexus)
           client-nexus      (get-client-nexus)
           dispatch-data     (case trigger
                               :event     (build-event-map js-data payload)
                               :lifecycle (build-lifecycle-map js-data payload)
                               {})
           client-actions    (filterv #(or (client-action? client-nexus %)
                                           (client-effect? client-nexus %)) actions)
           {:keys [effects]} (nexus/expand-actions client-nexus nil client-actions dispatch-data)
           server-effects    (filterv #(server-effect? server-nexus %) effects)
           server-actions    (filterv #(or (server-action? server-nexus %)
                                           (server-effect? server-nexus %)) actions)]
       {:client-effects (filterv #(client-effect? client-nexus %) effects)
        :client-actions client-actions
        :server-effects server-effects
        :server-actions server-actions
        :bad-actions    (filterv #(and (server-action? server-nexus %)
                                       (not (server-effect? server-nexus %)))
                                 effects)
        :dispatch-data  dispatch-data
        :server-payload (when-let [actions-to-send (seq (concat server-actions server-effects))]
                          (merge payload
                                 {:actions (nexus/interpolate client-nexus
                                                              dispatch-data
                                                              actions-to-send)}))})))

#?(:cljs
   (defn divert [payload-arg js-data]
     (let [server-nexus             (get-server-nexus)
           client-nexus             (get-client-nexus)
           payload                  (cond-> payload-arg (string? payload-arg) deserialize-fn)
           {:keys [client-effects
                   bad-actions
                   dispatch-data
                   server-payload]} (divert* payload js-data)]
       (when (seq bad-actions)
         (js/console.warn "🪐OFFWORLD: These keys were returned from an action handler: "
                          (pr-str (mapv first bad-actions))
                          "They're listed in the nexus as actions, not effects."
                          "In SSR mode, they won't be sent to the server (or executed at all)."
                          "This matches the behavior of CSR mode. By design, actions can't trigger actions."))
       (when @online?
         (when (seq client-effects)
           (nexus/dispatch client-nexus (atom {}) dispatch-data client-effects))
         (when server-payload
           (serialize-fn server-payload))))))

#?(:cljs
   (defn dispatch! [url actions & {:keys [event extra-payload trigger]}]
     (when-let [{:keys [server-payload client-effects]}
                (divert* {:actions actions :trigger trigger} event)]
       (let [d*-json   (js/JSON.stringify #js {:offworld (serialize-fn (merge server-payload extra-payload))})
             query-url (str url "?datastar=" (js/encodeURIComponent d*-json))]
         (js/fetch query-url #js {:method "GET"})))))

(defn offline? [stem]
  #?(:clj false
     :cljs (::offline? (meta stem))))

(defn with-modifiers [k v]
  (let [{:datastar/keys [modifiers]} (meta v)]
    (if-not modifiers
      k
      (keyword (apply str (name k) (interleave (repeat "__") (map name modifiers)))))))

(defn d*-dispatch [actions & {:keys [serialize-fn extra-payload dispatch-url]
                              :or   {serialize-fn  ou/serialize
                                     dispatch-url "/replicant-dispatch"}}]
  (str "((_sp)=>_sp&&@get('" dispatch-url "',{payload:{offworld:_sp}}))"
       "(nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions actions
                                 :trigger :event})) "',"
       "evt))"))

(defn d*-lifecycle [actions lifecycle & {:keys [serialize-fn extra-payload dispatch-url]
                                         :or   {serialize-fn  ou/serialize
                                                dispatch-url "/replicant-dispatch"}}]
  (str "((_sp)=>_sp&&@get('" dispatch-url "',{payload:{offworld:_sp}}))"
       "(nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions   actions
                                 :trigger   :lifecycle
                                 :lifecycle lifecycle})) "',"
       "el))"))

(defn attr->d*
  "Converts top-level hiccup attributes to datastar expressions.
  Returns a sorted-map, since datastar depends on some keys appearing
  earlier in the attributes."
  [{:as m :replicant/keys [on-unmount on-mount]} & {:as opts}]
  (cond-> m
    on-mount   (assoc (with-modifiers :data-init on-mount)
                      (d*-lifecycle on-mount :replicant/mount opts))
    on-unmount (assoc (with-modifiers :data-on-remove on-unmount)
                      (d*-lifecycle on-unmount :replicant/unmount opts))))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [m & {:as opts}]
  (into (dissoc m :on)
        (for [[k v] (:on m)]
          [(with-modifiers (keyword (str "data-on" k)) v) (d*-dispatch v opts)])))

(defn replicant->d* [hiccup & {:keys [dispatch-url] :as opts}]
  (let [opts (assoc-in opts [:extra-payload :dispatch-url] dispatch-url)]
    (walk/postwalk
     (fn [node] (cond-> node (map? node) (-> (#(on-hooks-replicant->d* % opts))
                                             (#(attr->d* % opts)))))
     hiccup)))

#?(:clj
   (defmacro defc
     {:clj-kondo/lint-as 'clojure.core/defn}
     [sym & decls]
     (let [k (str (ns-name *ns*) "/" sym)]
       `(do
          (k/defq ~sym ~@decls)
          (swap! registry assoc-in [:render-fn ~k] #?(:clj  (var ~sym)
                                                      :cljs ~sym))
          #?(:clj  (var ~sym)
             :cljs ~sym)))))

(comment
  (require '[nextjournal.baseline :as k])

  (k/defq a [stem] true)

  (k/defq b {::k/deps `a} [stem] (when (a stem) (:b stem)))

  (defc render-fn {::k/deps `b} [stem] (b stem))

  (k/trace (render-fn {})))
