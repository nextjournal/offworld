(ns nextjournal.offworld.staging
  "Runtime staging analysis for offworld dispatches.

  A *dispatch* is a vector of action-vectors, e.g.

      [[:effects/save path v]
       [:node/focus [::🌿/el id]]]

  Every registered action / effect / placeholder resolves at a fixed *stage* in
  the pipeline (C19's six, plus render as stage 0):

      0 render │ 1 client/state │ 2 client/expand │ 3 client/fx
               │ 4 server/state │ 5 server/expand │ 6 server/fx

  offworld's `kind × side` slots straight in. `side` is :server iff the handler
  is marked ^::🪐/server, else :client (the default). Each kind has a *deadline* —
  the stage past which an unresolved/unrun reference is stranded:

    - action: its side's *expand* stage (an action must expand there).
    - effect: its side's *fx* stage (an effect runs there).
    - placeholder: its side's *fx* stage. Interpolation runs twice per side
      (before expansion AND before effect handling), so a placeholder surviving
      expansion still has a pre-fx pass coming — its deadline is fx, not expand.

  ## The staging law and what is checkable

  The law (see ~/hybrid-ssr-playground §5.11): computation authored at stage N may
  *consume* only values already resolved by some stage ≤ N. It may *carry* — nest,
  restructure, pass through — a reference to a later-stage value as opaque data,
  but never compute with its contents before its stage arrives.

  Whether an expansion handler *consumes* or *carries* a value lives in its body
  and is undecidable from the dispatch data — so the FORWARD case (a later-stage
  ref inside an earlier action) can't be judged here; it might be a legal carry.

  What IS decidable is the BACKWARD case, because there carry has no destination:

  => VIOLATION `:stranded-client-ref` — a client-stage reference that survives
     into server-bound actions. It resolves only on the client; every client
     stage is already past and its resolver context (DOM/event) is gone. It can
     be neither consumed (no value) nor carried (nowhere left to carry it). No
     handler-body analysis required.

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
  only by computation at stage j >= i."
  [:render :client/state :client/expand :client/fx :server/state :server/expand :server/fx])

(def ^:private stage->n (into {} (map-indexed (fn [i s] [s i]) stage-order)))

(defn- stage-of
  "The stage keyword by which a `kind`×`side` reference must have resolved — its
  *deadline*, the point past which it is stranded — or nil.

  Note placeholders: interpolation runs twice per side (once before expansion,
  once before effect handling), so a placeholder may resolve at either pass. Its
  deadline is the *last* one, immediately before fx — NOT expansion. A placeholder
  surviving expansion is not yet stranded; one surviving fx is."
  [kind side]
  (case [kind side]
    [:placeholder :client] :client/fx      ; resolves at interp₁ or interp₂; deadline = pre-fx
    [:placeholder :server] :server/fx
    [:action      :client] :client/expand  ; :action and :expansion share the expansion
    [:action      :server] :server/expand  ; bucket in nexus — same deadline for both
    [:expansion   :client] :client/expand
    [:expansion   :server] :server/expand
    [:effect      :client] :client/fx
    [:effect      :server] :server/fx
    nil))

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
  "Classify a single key `k` against `nexus`. Returns
  {:key :kind :side :stage :n} for a registered key, or
  {:key :kind :unknown :side :unknown :stage nil :n nil} when registered nowhere."
  [nexus k]
  (or (some (fn [[kind reg-key]]
              (let [reg (get nexus reg-key)]
                (when (contains? reg k)
                  (let [side  (if (server-handler? (get reg k)) :server :client)
                        stage (stage-of kind side)]
                    {:key k :kind kind :side side :stage stage :n (stage->n stage)}))))
            buckets)
      {:key k :kind :unknown :side :unknown :stage nil :n nil}))

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
  "Pure. Client-stage references still present in `actions` — a server-bound
  payload, or actions seen at a server stage. Each resolves only on the client,
  a stage now in the past whose context is gone, so it can be neither consumed
  nor carried anywhere useful."
  [nexus actions]
  (into []
        (for [i     (refs nexus actions)
              :when (= :client (:side i))]
          {:type    :stranded-client-ref
           :key     (:key i)
           :kind    (:kind i)
           :stage   (:stage i)
           :message (str (name (:kind i)) " " (:key i) " is client-stage ("
                         (name (:stage i)) ") but survives into server-bound "
                         "actions — its resolver needs client context that no "
                         "longer exists, and every client stage is already past, "
                         "so it can be neither consumed nor carried")})))

;; ---------------------------------------------------------------------------
;; Runtime reporting — the "warn at runtime" surface. Off by default; flip with
;; (warn-on!). Observed violations accumulate in `observed` for later `report`.

(defonce ^{:doc "Whether divert* should warn on staging violations."} -warn?
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
  ;; A registry with a server effect, a client effect, and one placeholder of each side:
  (def nexus
    {:nexus/actions      {}
     :nexus/effects      {:fx/server (with-meta (fn []) {::🪐/server true})   ; server/fx (6)
                          :fx/client   (with-meta (fn []) {})}                  ; client/fx (3)
     :nexus/placeholders {:pl/client    (with-meta (fn []) {})                   ; client/expand (2)
                          :pl/server    (with-meta (fn []) {::🪐/server true})}}) ; server/expand (5)

  (lookup nexus :fx/server) ;=> {:kind :effect :side :server :stage :server/fx :n 6 ...}

  ;; LEAK: a client placeholder rode into the server payload (interpolation missed it)
  (stranded-at-server nexus [[:fx/server path [:pl/client "x"]]])
  ;;=> [{:type :stranded-client-ref :key :el/client :kind :placeholder ...}]

  ;; Clean: only a server placeholder in the server payload
  (stranded-at-server nexus [[:fx/server path [:pl/server "x"]]]) ;=> []

  (unregistered-actions nexus [[:effcts/save "typo"]])
  ;;=> [{:type :unregistered-action :key :effcts/save ...}]
  )
