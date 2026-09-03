(ns nextjournal.offworld
  (:require
   #?(:clj [clojure.walk :as walk])
   #?@(:cljs
       [#_[nextjournal.offworld.order :as 📈]
        [cljs.core :refer [IFn]]
        [core.lite :as 🪶]
        [nexus.registry :as nxr]
        [nextjournal.offworld.staging :as staging]])
   [datastar :as-alias 🚀]
   [nexus.core :as nexus]
   [nextjournal.offworld.stem :as-alias 🌿]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.util :as ou])
  #?(:cljs (:require-macros
            [nextjournal.offworld :refer [defc]])))

#?(:cljs (goog-define csr_bundle false))

(def registry (volatile! {}))

#?(:cljs (defonce memories (js/WeakMap.)))

#?(:cljs (def online? (reify IDeref (-deref [_] js/navigator.onLine))))

(defonce ux (volatile! :csr))

(defn set-ux! [k] (vreset! ux k))

(defn get-ux [] @ux)

#?(:cljs
   (defn recall [node]
     (case (get-ux)
       :csr nil
       :ssr (.get memories node))))

#?(:cljs (def ^:dynamic encode-fn ou/encode))
#?(:cljs (def ^:dynamic decode-fn ou/decode))

#?(:cljs (defn register-encode-fn! [f] (set! encode-fn f)))
#?(:cljs (defn register-decode-fn! [f] (set! decode-fn f)))

(declare dispatch!)

#?(:cljs
   (defn build-event-map [e _]
     (let [node (some-> e .-target)]
       (cond-> {:replicant/trigger   :replicant.trigger/dom-event
                :replicant/dom-event e}
         node (🪶/assoc :replicant/node node)))))

#?(:cljs
   (defn build-lifecycle-map [node payload]
     (let [lifecycle    (:lifecycle payload)
           dispatch-url (:dispatch-url payload)
           conn-id      (:conn-id payload)]
       (merge
        {:replicant/life-cycle lifecycle
         :replicant/node       node
         :replicant/remember   (fn remember [memory]
                                 (.set ^js memories node memory))
         ::🪐/dispatch
         (fn [actions]
           (dispatch! dispatch-url actions (merge payload
                                                  {:event         node
                                                   :trigger       :lifecycle
                                                   :extra-payload {:conn-id conn-id}})))}
        (when (not= lifecycle :replicant/mount)
          {:replicant/memory (recall node)})))))

(defn inject
  "What a render-fn takes at a seam a caller decides — the actions to run on
  a click, a scroll, a commit — given the values only the render-fn has.

  Three shapes, all render-time function application:

    a vector   an already-finished dispatch; params are dropped
    a keyword  an action the caller registered, called with params as its
               single map argument
    a function called with params, returns the dispatch

  A keyword is usually the one to reach for: one definition site, greppable,
  no closure at the call site, and a map argument the render-fn can add to
  without breaking any handler. Which shapes are available at a given seam
  is decided by world, not taste — a handler that reads server state must be
  marked ::🪐/server, and one that emits a client-world placeholder must not
  be, or the placeholder strands past `request`.

  A vector suits actions the caller can write out in full, since nothing is
  injected into it — the caller's actions compose with the render-fn's own
  by ordinary `into`, and nothing quietly appends arguments to a vector that
  reads as finished at its call site."
  [x params]
  (cond
    (keyword? x) [[x params]]
    (fn? x)      (x params)
    :else        x))

(defn server-marked? [x] (contains? (meta x) ::🪐/server))

(defn client-marked? [x] (and x (not (server-marked? x))))

(defn client-handled? [ux kind nexus [k]]
  (case ux
    :csr true
    :ssr (client-marked? (get-in nexus [kind k]))))

(defn server-handled? [ux kind nexus [k]]
  (case ux
    :csr false
    :ssr (server-marked? (get-in nexus [kind k]))))

(def ^:private buckets
  [:nexus/placeholders :nexus/expansions :nexus/actions :nexus/effects])

(defn handler-of [nexus [k]]
  (some #(get-in nexus [% k]) buckets))

(defn server-action? [nexus action]
  (server-marked? (handler-of nexus action)))

(defn divert-interceptor [{:keys [nexus action dispatch-data] :as ctx}]
  (if (and action (server-action? nexus action))
    (-> ctx
        (update ::🪐/server-actions (fnil conj [])
                (first (nexus/interpolate nexus dispatch-data [action])))
        (assoc :queue []))
    ctx))

(defn client-placeholders [nexus]
  (update nexus :nexus/placeholders
          #(into {} (filter (comp client-marked? val)) %)))

(defn client-nexus [nexus]
  (-> nexus
      client-placeholders
      (update :nexus/interceptors (fnil conj [])
              {:phase         ::🪐/divert
               :before-action divert-interceptor})))

(defn pre-interpolate [nexus dispatch-data actions]
  (nexus/interpolate (client-placeholders nexus) dispatch-data actions))

#?(:cljs
   (defn divert* [payload js-data]
     (let [actions        (:actions payload)
           trigger        (:trigger payload)
           nexus          (nxr/get-registry)
           dispatch-data  (case trigger
                            :event     (build-event-map js-data payload)
                            :lifecycle (build-lifecycle-map js-data payload)
                            {})
           ssr?           (= :ssr (get-ux))
           ctx            (nexus/dispatch (cond-> nexus ssr? client-nexus)
                                          (atom {}) dispatch-data actions)
           server-actions (::🪐/server-actions ctx)]
       (when (staging/warning?)
         (staging/warn! (into (staging/unregistered-actions nexus actions)
                              (staging/stranded-at-server nexus server-actions))))
       (cond-> {:dispatch-data dispatch-data}
         (seq server-actions)
         (🪶/assoc :server-payload
                   (🪶/assoc payload :actions
                             (with-meta (vec server-actions)
                               (merge (meta actions) (meta server-actions)))))))))

#?(:cljs
   (defn ^:export divert [payload-arg js-data]
     (when @online?
       (let [payload (cond-> payload-arg (string? payload-arg) decode-fn)]
         (some-> (divert* payload js-data) :server-payload encode-fn)))))

#?(:cljs
   (defn dispatch! [url actions & {:keys [event extra-payload trigger]}]
     (when-let [server-payload (:server-payload
                                (divert* {:actions actions :trigger trigger} event))]
       (let [d*-json (js/JSON.stringify
                      #js {:offworld (encode-fn (merge server-payload extra-payload))})]
         (js/fetch url #js {:method  "POST"
                            :headers #js {"Content-Type" "application/json"}
                            :body    d*-json})))))

#?(:clj
   (defn with-modifiers [k v]
     (let [{:datastar/keys [modifiers]} (meta v)]
       (if-not modifiers
         k
         (keyword (apply str (name k) (interleave (repeat "__") (map name modifiers))))))))

(defn d*-dispatch [actions & {:keys [serialize-fn extra-payload dispatch-url]
                              :or   {serialize-fn ou/encode}}]
  (str "((_sp)=>_sp&&@post('" dispatch-url "',{payload:{offworld:_sp}}))"
       "(nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions actions
                                 :trigger :event})) "',"
       "evt))"))

(defn d*-lifecycle [actions lifecycle & {:keys [serialize-fn extra-payload dispatch-url]
                                         :or   {serialize-fn ou/encode}}]
  (str "((_sp)=>_sp&&@post('" dispatch-url "',{payload:{offworld:_sp}}))"
       "(nextjournal.offworld.divert("
       "'" (serialize-fn (merge extra-payload
                                {:actions   actions
                                 :trigger   :lifecycle
                                 :lifecycle lifecycle})) "',"
       "el))"))

#?(:clj
   (defn attr->d*
     "Converts top-level hiccup attributes to datastar expressions.
  Returns a sorted-map, since datastar depends on some keys appearing
  earlier in the attributes."
     [{:as m :replicant/keys [on-unmount on-mount]} & {:as opts}]
     (cond-> m
       on-mount   (assoc (with-modifiers :data-init on-mount)
                         (d*-lifecycle on-mount :replicant/mount opts))
       on-unmount (assoc (with-modifiers :data-on-remove on-unmount)
                         (d*-lifecycle on-unmount :replicant/unmount opts)))))

#?(:clj
   (defn on-hooks-replicant->d*
     "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@post('/offworld-dispatch', {payload: '[[:my-action]]'})\"}"
     [m & {:as opts}]
     (into (dissoc m :on)
           (for [[k v] (:on m)]
             [(with-modifiers (keyword (str "data-on" k)) v) (d*-dispatch v opts)]))))

#?(:clj
   (defn replicant->d*
     [hiccup & {:as   opts
                :keys [dispatch-url]
                :or   {dispatch-url "/offworld-dispatch"}}]
     (let [opts (-> opts
                    (assoc :dispatch-url dispatch-url)
                    (assoc-in [:extra-payload :dispatch-url] dispatch-url))]
       (walk/postwalk
        (fn [node] (cond-> node (map? node) (-> (#(on-hooks-replicant->d* % opts))
                                                (#(attr->d* % opts)))))
        hiccup))))

(defmacro defc
  {:clj-kondo/lint-as 'clojure.core/defn}
  [sym & decls]
  (let [k (str (ns-name *ns*) "/" sym)]
    `(do
       (defn ~sym ~@decls)
       #_(swap! registry assoc-in [:render-fn ~k] #?(:clj  (var ~sym)
                                                     :cljs ~sym)))))
