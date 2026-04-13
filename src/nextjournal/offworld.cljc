(ns nextjournal.offworld
  (:require
   [clojure.walk :as walk]
   [datastar :as-alias 🚀]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.baseline :as-alias k]
   [nextjournal.offworld.util :as ou]
   #?@(:cljs
       [[nexus.core :as nexus]
        [replicant.dom :as rdom]]))
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

#?(:cljs
   (defn build-event-map [e]
     (let [node (.-target e)]
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
   (defn divert [actions-str js-data]
     (let [server-nexus                          (get-server-nexus)
           client-nexus                          (get-client-nexus)
           {:keys [actions trigger] :as payload} (deserialize-fn actions-str)
           dispatch-data                         (case trigger
                                                   :event     (build-event-map js-data)
                                                   :lifecycle (build-lifecycle-map js-data)
                                                   {})
           client-actions                        (filterv #(or (client-action? client-nexus %)
                                                               (client-effect? client-nexus %)) actions)
           server-actions                        (filterv #(or (server-action? server-nexus %)
                                                               (server-effect? server-nexus %)) actions)
           {:keys [effects]}                     (nexus/expand-actions client-nexus nil client-actions dispatch-data)
           client-effects                        (filterv #(client-effect? client-nexus %) effects)
           server-effects                        (filterv #(server-effect? server-nexus %) effects)
           bad-actions                           (filterv #(and (server-action? server-nexus %)
                                                                (not (server-effect? server-nexus %)))
                                                          effects)
           actions-to-send                       (seq (concat server-effects server-actions))]
       (when (seq bad-actions)
         (js/console.warn "🪐OFFWORLD: These keys were returned from an action handler: "
                          (pr-str (mapv first bad-actions))
                          "They're listed in the nexus as actions, not effects."
                          "In SSR mode, they won't be sent to the server (or executed at all)."
                          "This matches the behavior of CSR mode. By design, actions can't trigger actions."))
       (when @online?
         (nexus/dispatch client-nexus (atom {}) dispatch-data client-effects)
         (serialize-fn (merge payload
                              {:actions (nexus/interpolate client-nexus
                                                           dispatch-data
                                                           (vec actions-to-send))}))))))

(defn offline? [stem]
  #?(:clj false
     :cljs (::offline? (meta stem))))

(defn d*-dispatch [actions & {:keys [serialize-fn extra-payload dispatch-path]
                              :or   {serialize-fn  ou/serialize
                                     dispatch-path "/replicant-dispatch"}}]
  (str "@get('" dispatch-path "', "
       "{payload: {offworld: nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions actions
                                 :trigger :event})) "', "
       "evt" ")}})"))

(defn d*-dispatch-init [actions & {:keys [serialize-fn extra-payload dispatch-path]
                                   :or   {serialize-fn ou/serialize
                                          dispatch-path "/replicant-dispatch"}}]
  (str "@get('" dispatch-path "', "
       "{payload: {offworld: nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions actions
                                 :trigger :lifecycle})) "', "
       "$offworld_ref" ")}})"))

(defn attr->d*
  "Converts top-level hiccup attributes to datastar expressions.
  Returns a sorted-map, since datastar depends on some keys appearing
  earlier in the attributes."
  [{:as m ::🚀/keys [data-init]} & {:as opts}]
  (if-not data-init
    m
    (into (ou/priority-sorted-map [:data-ref])
          (merge m
                 {:data-ref  "offworld_ref"
                  :data-init (d*-dispatch-init data-init opts)}))))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [m & {:as opts}]
  (into (dissoc m :on)
        (for [[k v] (:on m)
              :let  [{:datastar/keys [modifiers]} (meta v)]]
          [(keyword (apply str "data-on" k (interleave (repeat "__")
                                                       (map name modifiers))))
           (d*-dispatch v opts)])))

(defn replicant->d* [hiccup & {:as opts}]
  (walk/postwalk
   (fn [node] (cond-> node (map? node) (-> (#(on-hooks-replicant->d* % opts))
                                           (#(attr->d* % opts)))))
   hiccup))

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
