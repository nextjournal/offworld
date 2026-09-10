(ns nextjournal.offworld.staging
  "Runtime staging analysis for offworld dispatches.

  A *dispatch* is a vector of action-vectors, e.g.

      [[:effects/save path v]
       [:node/focus [::🌿/el id]]]

  Every registered action / effect / placeholder resolves in one of two *worlds*,
  and those worlds sit in a five-stage pipeline:

      0 render │ 1 morph │ 2 client │ 3 request │ 4 server

  `world` is :server iff the handler is marked ^::🪐/server, else :client (the
  default) — and that world IS the stage it resolves at. There is no finer
  deadline inside a world. Nexus drains a dispatch to completion, so an expansion
  emitted by an expansion still expands, and a placeholder surviving expansion
  still meets the pre-fx interpolation pass. Being *late* within a world is not
  something that can happen.

  Three of the five stages are not registration targets. `render` is ordinary
  Clojure at the authoring site. `morph` and `request` are transports: nothing
  authored resolves there, but real time passes and messages can be lost,
  delayed, reordered or forged — see `nextjournal.offworld.order`.

  `request` is also the boundary the decidable violation is stated against. It is
  where the server-bound remainder is serialized, so a client reference still
  standing as a vector by then arrives on the server as inert data.

  ## The staging law and what is checkable

  The law (see ~/hybrid-ssr-playground §5.11): computation authored at stage N may
  *consume* only values already resolved by some stage ≤ N. It may *carry* — nest,
  restructure, pass through — a reference to a later-stage value as opaque data,
  but never compute with its contents before its stage arrives.

  Whether an expansion handler *consumes* or *carries* a value lives in its body
  and is undecidable from the dispatch data — so the FORWARD case (a later-stage
  ref inside an earlier action) can't be judged here; it might be a legal carry.

  What IS decidable is the BACKWARD case, because there carry has no destination:

  => VIOLATION `:stranded-client-ref` — a client-world reference that survives
     past `request` into server-bound actions. It resolves only on the client,
     that stage is now behind it, and its resolver context (DOM/event) is gone.
     It can be neither consumed (no value) nor carried (nowhere left to carry
     it). No handler-body analysis required.

  => VIOLATION `:unregistered-action` — a dispatched action head that resolves to
     no registered handler: it runs as a silent no-op.

  Everything is a pure fn of [nexus …]; no deref, no side effects (except
  `warn!`/`report`, which are clearly marked)."
  (:require
   [clojure.string :as str]
   [nextjournal.offworld :as-alias 🪐]))

;; ---------------------------------------------------------------------------
;; The stage ladder.

(def stage-order
  "The pipeline's fixed stage order. A value resolved at stage i may be consumed
  only by computation at stage j >= i.

  Only :client and :server are registration targets — a handler's world is its
  stage, and no finer rung is checkable, because nexus drains a dispatch to
  completion within a world. :render is ordinary Clojure at the authoring site;
  :morph and :request are transports where nothing authored resolves, named
  because real time passes there and messages can be lost, delayed, reordered or
  forged."
  [:render :morph :client :request :server])

;; ---------------------------------------------------------------------------
;; Stage lookup — the one place that reads registry metadata.

(def ^:private buckets
  "Registration kind -> its key in the nexus registry map, ordered by nexus's own
  resolution precedence: a key registered under several kinds is classified by the
  first that matches (see `lookup`). The order mirrors what nexus does at runtime:

    placeholder  interpolation runs first and replaces the form wholesale, before
                 anything looks at it as an expansion or effect — so it always wins
    expansion    `expand-actions` checks :nexus/expansions first…
    action       …then :nexus/actions (a legacy alias for expansions — never
                 populated by the current register fns, but kept because nexus is
                 a library and won't drop the fallback)
    effect       the drain — only reached when nothing above handles the head

  A vector, not a map: the order is load-bearing, and a map literal only preserves
  insertion order by accident (array-map, ≤ 8 entries)."
  [[:placeholder :nexus/placeholders]
   [:expansion   :nexus/expansions]
   [:action      :nexus/actions]
   [:effect      :nexus/effects]])

(defn- server-handler? [h] (contains? (meta h) ::🪐/server))

(defn lookup
  "Classify a single key `k` against `nexus`. Returns {:key :kind :world} for a
  registered key, or {:key :kind :unknown :world :unknown} when registered
  nowhere. The world is the stage; see `stage-order`."
  [nexus k]
  (or (some (fn [[kind reg-key]]
              (let [reg (get nexus reg-key)]
                (when (contains? reg k)
                  {:key   k
                   :kind  kind
                   :world (if (server-handler? (get reg k)) :server :client)})))
            buckets)
      {:key k :kind :unknown :world :unknown}))

;; ---------------------------------------------------------------------------
;; Tagging — pure, attaches ::info metadata to every keyword-headed vector.

(defn keyword-headed?
  "A vector whose first element is a keyword — the shape of both a dispatched
  action `[:effects/save ...]` and a reference `[::🌿/el id]`."
  [x]
  (and (vector? x) (keyword? (first x))))

(defn tag
  "Walk `form` and attach `{::info (lookup nexus head)}` to every keyword-headed
  vector, recursing through args, vectors, seqs and maps. Pure."
  [nexus form]
  (cond
    (keyword-headed? form)
    (vary-meta (into [(first form)] (map #(tag nexus %)) (rest form))
               assoc ::info (lookup nexus (first form)))

    (vector? form) (mapv #(tag nexus %) form)
    (seq? form)    (map #(tag nexus %) form)
    (map? form)    (reduce-kv (fn [m k v] (assoc m k (tag nexus v))) (empty form) form)
    :else          form))

(defn info
  "The ::info map attached to a tagged node, or nil."
  [node]
  (::info (meta node)))

(defn refs
  "Every keyword-headed vector anywhere in `form` (registered or not), as its
  ::info map. Pure."
  [nexus form]
  (->> (tree-seq coll? seq (tag nexus form))
       (filter keyword-headed?)
       (map info)))

;; ---------------------------------------------------------------------------
;; The checks.

(defn- top-actions
  "The top-level actions of a (tagged) dispatch — a single action or the usual
  vector-of-actions."
  [tagged]
  (if (keyword-headed? tagged) [tagged] (filter keyword-headed? tagged)))

(defn unregistered-actions
  "Pure. Dispatched action heads in `dispatch` that resolve to no handler."
  [nexus dispatch]
  (into []
        (for [a     (top-actions (tag nexus dispatch))
              :let  [i (info a)]
              :when (= :unknown (:kind i))]
          {:type    :unregistered-action
           :key     (:key i)
           :message (str "dispatched action " (:key i)
                         " is not registered (runs as a silent no-op)")})))

(defn stranded-at-server
  "Pure. Client-world references still present in `actions` — a server-bound
  payload, or actions seen at a server stage. Each had to resolve before the
  `request` stage; past it the client context its resolver needs is gone, so it
  can be neither consumed nor carried anywhere useful."
  [nexus actions]
  (into []
        (for [i     (refs nexus actions)
              :when (= :client (:world i))]
          {:type    :stranded-client-ref
           :key     (:key i)
           :kind    (:kind i)
           :world   (:world i)
           :message (str (name (:kind i)) " " (:key i) " is client-world but "
                         "survives into server-bound actions — it had to resolve "
                         "before the request stage, and the client context its "
                         "resolver needs no longer exists, so it can be neither "
                         "consumed nor carried")})))

;; ---------------------------------------------------------------------------
;; Runtime reporting — the "warn at runtime" surface. Off by default; flip with
;; (warn-on!). Observed violations accumulate in `observed` for later `report`.

(defonce ^{:doc "Whether the runtime checks warn on staging violations."} -warn?
  (volatile! false))

(defn warn-on!  [] (vreset! -warn? true))
(defn warn-off! [] (vreset! -warn? false))
(defn warning?  [] @-warn?)

(defonce ^{:doc "Session log of observed violations, for `report`."} observed
  (atom []))

(defn clear-observed! [] (reset! observed []))

(defn- log! [s]
  #?(:clj  (binding [*out* *err*] (println s))
     :cljs (js/console.warn s)))

(defn warn!
  "Side-effecting. Log `violations` (if any), record them in `observed`, and
  return them. No-op when empty."
  [violations]
  (when-let [vs (seq violations)]
    (swap! observed into vs)
    (log! (str "⚠ offworld staging: " (count vs) " violation(s):\n"
               (str/join "\n" (map #(str "  • " (:message %)) vs)))))
  violations)

(defn checker
  "A `:before-action` interceptor running the staging checks against every action
  Nexus reaches — at any expansion depth, in either world, on whatever the
  handlers actually produced rather than on what the source happened to spell.
  Silent unless `warn-on!` has been called.

  Install it before `nextjournal.offworld/client-nexus` appends the divert
  interceptor: interceptors run in order, and divert empties the queue for the
  actions it claims, so anything after it never sees them.

  `:world` decides whether stranded-client-ref is checked, since a client-stage
  reference is only stranded once it has reached a server stage; it defaults to
  the runtime this was compiled for. `:on-violation` defaults to `warn!`."
  ([] (checker {}))
  ([{:keys [world on-violation]
     :or   {world #?(:clj :server :cljs :client)}}]
   (let [report! (or on-violation warn!)]
     {:phase ::check
      :before-action
      (fn [{:keys [nexus action] :as ctx}]
        (when (and action (warning?))
          (let [vs (cond-> (unregistered-actions nexus action)
                     (= :server world) (into (stranded-at-server nexus action)))]
            (when (seq vs) (report! vs))))
        ctx)})))

(defn report
  "Side-effecting. Print a grouped summary of everything seen this session."
  []
  (let [vs @observed]
    (if (empty? vs)
      (log! "offworld staging: no violations observed")
      (log! (str "offworld staging report — " (count vs) " violation(s):\n"
                 (->> (group-by :type vs)
                      (map (fn [[t items]]
                             (str "  " (name t) " (" (count items) "):\n"
                                  (->> items (map #(str "    - " (:key %))) distinct (str/join "\n")))))
                      (str/join "\n\n")))))
    vs))

(comment
  ;; A registry with a server effect, a client effect, and one placeholder of each world:
  (def nexus
    {:nexus/actions      {}
     :nexus/effects      {:fx/server (with-meta (fn []) {::🪐/server true})    ; server (4)
                          :fx/client (with-meta (fn []) {})}                    ; client (2)
     :nexus/placeholders {:pl/client (with-meta (fn []) {})                     ; client (2)
                          :pl/server (with-meta (fn []) {::🪐/server true})}})  ; server (4)

  (lookup nexus :fx/server) ;=> {:key :fx/server :kind :effect :world :server}

  ;; LEAK: a client placeholder rode into the server payload (interpolation missed it)
  (stranded-at-server nexus [[:fx/server path [:pl/client "x"]]])
  ;;=> [{:type :stranded-client-ref :key :el/client :kind :placeholder ...}]

  ;; Clean: only a server placeholder in the server payload
  (stranded-at-server nexus [[:fx/server path [:pl/server "x"]]]) ;=> []

  (unregistered-actions nexus [[:effcts/save "typo"]])
  ;;=> [{:type :unregistered-action :key :effcts/save ...}]
  )

;; ---------------------------------------------------------------------------
;; The stage plan — what an intent is, read without running it.

(defn describe
  "One action or reference as a tree: its key, kind and stage, and the same for
  every reference nested inside it.

  Possible only because an intent is a value. A framework that compiles the
  boundary into strings has nothing to walk here: the expression is opaque to
  its own author, so there is no reading of it that does not involve running
  it."
  [nexus node]
  (if (keyword-headed? node)
    (let [{:keys [key kind world]} (lookup nexus (first node))
          args                     (mapv #(describe nexus %) (rest node))]
      (cond-> {:key key :kind kind :stage world}
        (seq args) (assoc :args args)))
    (cond
      (vector? node) (mapv #(describe nexus %) node)
      (map? node)    (reduce-kv (fn [m k v] (assoc m k (describe nexus v))) (empty node) node)
      :else          node)))

(defn- nodes
  [described]
  (filter (every-pred map? :key) (tree-seq coll? #(if (map? %) (:args %) (seq %)) described)))

(defn by-stage
  "Every reference in a described intent, grouped by the stage that resolves it,
  in pipeline order."
  [described]
  (let [all (nodes described)]
    (into []
          (keep (fn [stage]
                  (when-let [at (seq (filter (comp #{stage} :stage) all))]
                    {:stage stage :steps (vec at)})))
          (concat stage-order [:unknown]))))

(defn plan
  "The staged shape of one dispatch, and whether it holds together.

  The stranded-client-ref check is deliberately not run here: a client
  placeholder sitting inside a server-bound action is the ordinary case, since
  it resolves on the client before the request. That check belongs to what
  survives diversion, not to what a render declared.

  Three things fall out of the intent being data, and each is something a
  compiled-string boundary cannot offer: the pipeline can be read off the page
  without executing it, every reference can be attributed to the stage that
  resolves it, and the invariants are decidable rather than conventional."
  [nexus dispatch]
  (let [actions   (if (keyword-headed? dispatch) [dispatch] (vec dispatch))
        described (mapv #(describe nexus %) actions)]
    {:actions    described
     :by-stage   (by-stage described)
     :violations (unregistered-actions nexus dispatch)}))
