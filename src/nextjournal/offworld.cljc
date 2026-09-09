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
   [nextjournal.offworld.divert :as divert]
   [nextjournal.offworld.stem :as-alias 🌿]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.util :as ou])
  #?(:cljs (:require-macros
            [nextjournal.offworld :refer [defc]])))

#?(:cljs (goog-define csr_bundle false))

(def registry (volatile! {}))

#?(:cljs (defonce memories (js/WeakMap.)))

(defonce ux (volatile! :csr))

(defn set-ux! [k] (vreset! ux k))

(defn get-ux [] @ux)

#?(:cljs
   (defn recall [node]
     (case (get-ux)
       :csr nil
       :ssr (.get memories node))))

#?(:cljs (defonce dispatch-url (volatile! "/offworld-dispatch")))

#?(:cljs (defn set-dispatch-url! [url] (vreset! dispatch-url url)))

#?(:cljs (defn get-dispatch-url [] @dispatch-url))

#?(:cljs (def ^:dynamic encode-fn ou/encode))

#?(:cljs (defn register-encode-fn! [f] (set! encode-fn f)))

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
           dispatch-url (or (:dispatch-url payload) (get-dispatch-url))
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

(def server-marked? divert/server-marked?)

(def client-marked? divert/client-marked?)

(def handler-of divert/handler-of)

(def server-action? divert/server-action?)

(def divert-interceptor divert/divert-interceptor)

(def client-placeholders divert/client-placeholders)

(def client-nexus divert/client-nexus)

(defn client-handled? [ux kind nexus [k]]
  (case ux
    :csr true
    :ssr (client-marked? (get-in nexus [kind k]))))

(defn server-handled? [ux kind nexus [k]]
  (case ux
    :csr false
    :ssr (server-marked? (get-in nexus [kind k]))))

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
   (defn dispatch! [url actions & {:keys [event extra-payload trigger]}]
     (when-let [server-payload (:server-payload
                                (divert* {:actions actions :trigger trigger} event))]
       (js/fetch url #js {:method  "POST"
                          :headers #js {"Content-Type" "application/json"}
                          :body    (encode-fn (merge server-payload extra-payload))}))))

#?(:clj
   (defn with-modifiers [k v]
     (let [{:datastar/keys [modifiers]} (meta v)]
       (if-not modifiers
         k
         (keyword (apply str (name k) (interleave (repeat "__") (map name modifiers))))))))

(defn intent-attr
  "The attribute holding the intent for `k`, an event name or a lifecycle name."
  [k]
  (keyword (str "data-intent" k)))

(defn d*-dispatch
  "The expression that runs the intent held beside it.

  A constant: it interpolates nothing and carries no payload, because the
  payload is the sibling attribute's whole value. The event and its modifiers
  stay on the `data-on` key, so debounce, throttle and the rest come from
  Datastar rather than from anything here."
  [_actions & _opts]
  "@intent(evt)")

(defn d*-lifecycle
  "The same expression for a lifecycle hook, which has no event to key on."
  [k]
  (str "@intent('" (name k) "')"))

(def render-only-meta
  "Metadata the attributes themselves consume, which therefore has no business
  being transmitted. Modifiers are the case in hand: they become part of the
  `data-on` key at render time, so an encoder that carries metadata was sending
  them for nothing."
  #{:datastar/modifiers})

(defn- for-the-wire [actions]
  (cond-> actions
    (seq (meta actions)) (vary-meta #(apply dissoc % render-only-meta))))

(defn intent
  "The serialized intent an attribute carries.

  `serialize-fn` is the wire format, and it is injected rather than chosen here
  on purpose: the format is the contract between a view and whatever interprets
  the attribute in the browser, which need not be Clojure at all.

  Whatever the format, it lands in an attribute value, so the renderer has to
  escape it. Replicant's string renderer did not until recently, which the old
  encoding never noticed: base64 and a single-quoted expression contain no
  double quote, and JSON is all double quotes."
  [payload & {:keys [serialize-fn extra-payload]
              :or   {serialize-fn ou/encode}}]
  (serialize-fn (-> (merge extra-payload payload)
                    (update :actions for-the-wire))))

#?(:clj
   (defn attr->d*
     "Converts top-level hiccup attributes to datastar expressions.
  Returns a sorted-map, since datastar depends on some keys appearing
  earlier in the attributes."
     [{:as m :replicant/keys [on-unmount on-mount]} & {:as opts}]
     (cond-> m
       on-mount   (assoc (with-modifiers :data-init on-mount)
                         (d*-lifecycle :mount)
                         (intent-attr :mount)
                         (intent {:actions   on-mount
                                  :trigger   :lifecycle
                                  :lifecycle :replicant/mount}
                                 opts))
       on-unmount (assoc (with-modifiers :data-on-remove on-unmount)
                         (d*-lifecycle :unmount)
                         (intent-attr :unmount)
                         (intent {:actions   on-unmount
                                  :trigger   :lifecycle
                                  :lifecycle :replicant/unmount}
                                 opts)))))

#?(:clj
   (defn on-hooks-replicant->d*
     "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-intent:click \"[[\\\"my-action\\\"]]\" :data-on:click \"@intent(evt)\"}

  Two attributes rather than one expression carrying an encoded payload. The
  intent is the value of an attribute of its own, so nothing about it is inside
  an expression, and the expression that runs it is a constant."
     [m & {:as opts}]
     (into (dissoc m :on)
           (mapcat (fn [[k v]]
                     [[(intent-attr k) (intent {:actions v
                                                :trigger :event}
                                               opts)]
                      [(with-modifiers (keyword (str "data-on" k)) v)
                       (d*-dispatch v opts)]])
                   (:on m)))))

#?(:clj
   (defn replicant->d*
     [hiccup & {:as opts}]
     (walk/postwalk
      (fn [node] (cond-> node (map? node) (-> (#(on-hooks-replicant->d* % opts))
                                              (#(attr->d* % opts)))))
      hiccup)))

(defmacro defc
  {:clj-kondo/lint-as 'clojure.core/defn}
  [sym & decls]
  (let [k (str (ns-name *ns*) "/" sym)]
    `(do
       (defn ~sym ~@decls)
       #_(swap! registry assoc-in [:render-fn ~k] #?(:clj  (var ~sym)
                                                     :cljs ~sym)))))
