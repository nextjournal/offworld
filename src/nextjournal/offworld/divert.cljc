(ns nextjournal.offworld.divert
  "Which world an action belongs to, and how the client hands over the rest.

  A view names actions the client has no implementation for, because they are
  the server's to run. So the rule is that an action the client can handle runs
  on the client and an action it cannot belongs to the server: not knowing an
  action is the signal. Placeholders follow the same rule for free, since
  `client-placeholders` is what the client interpolates against and a
  server-marked one is simply absent from it.

  `divert-interceptor` is a Nexus `:before-action` interceptor rather than a
  pass over the dispatch, so classification happens per action as the registry
  drains them — which is what lets a client expansion return anything at all,
  including actions and placeholders belonging to either world. An action minted
  mid-dispatch is classified like any other, however deep the expansion that
  produced it.

  This namespace requires `nexus.core` and nothing else, deliberately: it is the
  half of offworld that has to run wherever a client does, including under a
  Clojure-to-JavaScript transpiler that has no ClojureScript runtime. Keep it
  that way — no `cljs.core`, no macros, no Closure."
  (:require
   [nexus.core :as nexus]
   [nextjournal.offworld :as-alias 🪐]))

(defn server-marked?
  "Whether `x`'s metadata carries the server marker."
  [x]
  (contains? (meta x) ::🪐/server))

(defn client-marked?
  "Whether `x` exists and is not server-marked. The client's half is the
  default, so a handler earns the server by saying so."
  [x]
  (and x (not (server-marked? x))))

(def ^:private buckets
  [:nexus/placeholders :nexus/expansions :nexus/actions :nexus/effects])

(defn handler-of
  "The registered handler behind an action's head, whichever bucket holds it."
  [nexus [k]]
  (some #(get-in nexus [% k]) buckets))

(defn server-action?
  "Whether the handler behind this action belongs to the server."
  [nexus action]
  (server-marked? (handler-of nexus action)))

(defn divert-interceptor
  "Hand a server-bound action upstream instead of running it here.

  Interpolates it against the nexus in play — the client's, whose placeholders
  are already filtered to the client's own, so the client values inside resolve
  while the server's travel on as data — accumulates it under
  `::🪐/server-actions`, and empties the queue so nothing executes it locally."
  [{:keys [nexus action dispatch-data] :as ctx}]
  (if (and action (server-action? nexus action))
    (-> ctx
        (update ::🪐/server-actions (fnil conj [])
                (first (nexus/interpolate nexus dispatch-data [action])))
        (assoc :queue []))
    ctx))

(defn client-placeholders
  "The nexus with only the placeholders the client is allowed to resolve."
  [nexus]
  (update nexus :nexus/placeholders
          #(into {} (filter (comp client-marked? val)) %)))

(defn client-nexus
  "The nexus the client dispatches against: its own placeholders, plus the
  interceptor that diverts everything belonging to the server."
  [nexus]
  (-> nexus
      client-placeholders
      (update :nexus/interceptors (fnil conj [])
              {:phase         ::🪐/divert
               :before-action divert-interceptor})))
