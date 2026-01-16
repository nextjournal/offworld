(ns nextjournal.table.sketches
  (:require
   [clojure.edn :as edn]
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.table.ui :as ui]
   [nextjournal.table.clerk-viewers :as viewers]
   [nextjournal.offworld :as 🪐]
   [nexus.core :as nexus]
   [replicant.core :as replicant]))

;; # Sketches with replicant, datastar & tables
;; Some research questions follow, along with our findings.
;; To demonstrate our findings, this project includes a clj webserver and a cljs client.
;; To launch the server and build the client, run `user/start!`.
;; Then, visit `http://localhost:8000`.
;; Some demos implement server-side rendering. In that case, visit `http://localhost:8000?ssr=true`.

;; ## What does a full-featured dropdown (e.g. from ductile) look like when built from replicant?

;; ## Can we still use dom watchers like "Resize"?
;; replace the react functional ref pattern with replicant's :remember

;; ## How do we organize the "path" of a UI component?
;; - Need to pass down values and their conj'ed up paths
;; - "I just want some component local state" - now all of my parents must take care to build up a path
;; - Every value that might be changed needs its corresponding path passed down as well
;;   - `value` & `path`
;; - Need to garbage collect transient state on DOM element unmount
;;  (There are no components, thus no component lifecycle. Must hook into DOM node lifecycle)

;; ## Can we design an API for data grids in clerk & ductile?

;; ## Can we build a `nested-grid` in cljs using replicant's "top-down" UI model?

;; Commit [7ecd60b](https://github.com/nextjournal/tabla/commit/7ecd60b/)
;; adds a component based on re-com's [`nested-grid`](https://re-com.day8.com.au/#/nested-grid).
;; `nested-grid` helps users explore large datasets by rendering a scrollable rectangular "window" of the data.
;; The search algorithm is the same, and the render-fn is stripped down and converted to
;; use replicant-flavored hiccup.
;;
;; Here's a picture of the worst-case performance of this stripped-down component:

(clerk/image "http://localhost:8000/img/d6f5737-cljs-render-perf.png")

;; It's rendering a "window" of 4,000 cells, out of a dataset of 250,000.
;; The render-fn, including nested-grid's search algorithm, takes 30ms.
;;
;; Once replicant gets the hiccup from the render-fn, it mutates the DOM, adding 4,000
;; totally new elements. Replicant takes 500ms. Then the browser takes over, taking 200ms
;; to draw the new DOM.
;;
;; Notes:
;;
;; - With replicant we control the render call explicitly, so it's easy to set up profiling scenarios like this one.
;; - For a while, the render-fn was getting called twice. I didn't realize that returning two effect-vectors from
;;   the action handler caused two separate swaps and two render calls ([a40100a](https://github.com/nextjournal/tabla/commit/a40100a/)).
;; - So far, this doesn't seem much slower than the original reagent version.
;;
;; ## Datastar "morphing" grid demo
;; Visit `localhost:8000?ssr=true` to run nested-grid with server-side rendering. Still evaluating this,
;; but it seems to work nicely so far.
;;
;; ## What grid features can we offer the user?
;; - ordering?
;; - "computed" columns?
;; - filtering
;; - labels
;; - show-header?
;; - similar to `re-com.table-filter`
;; - Summary cells (powered by SSR) ([e.g.](https://observablehq.com/d/6d8a31a315f4ad94))
;;
;; ## How can datastar, replicant & nexus delegate responsibilities in SSR mode?
;;
;; We'd like to build an application from a single clojure expression,
;; which can run either fully on the client, or with a client/server pattern, using server-side-rendering.
;;
;; Client-only mode seems simple enough - we just build a conventional replicant/nexus app.
;; SSR mode is trickier to get right.
;;
;; To articulate a full event/render loop, we need to cover these responsibilities:
;;
;; 1) Dispatch actions
;; 1) Interpolate actions (requires: dom-event)
;; 1) Expand actions      (requires: dom-event, system-state)
;; 1) Interpolate effects (requires: dom-event, system-state)
;; 1) Batch effects
;; 1) Execute effects     (requires: dom-event, system-state)
;; 1) Render hiccup
;; 1) Render html
;; 1) Mutate DOM
;;
;; A few requirements constrain our choice of framework & runtime to which we can delegate each step:
;;
;; - The _dom-event_ only has value in the client.
;; - The _system-state_ could be stored anywhere, but we should constrain it to either client or server —
;;   otherwise, we'd coordinate two _system-states_, undermining the simplicity advantage of SSR.
;; - Replicant & nexus can run on the server, on the client, or _within_ datastar expressions.
;; - Datastar expressions can only run on the client.
;;
;; ### Concept A: Replicant SSR with Datastar at the edges
;;
;; Intuitively, I feel the cleanest design would be:
;; - Dispatch actions: datastar          (first on the client, then on the server.)
;; - Everything else:  replicant & nexus (on the server)
;; - Mutate DOM:       datastar          (on the client)
;;
;; We can achieve this if we follow one convention, and one operational rule:
;;
;; 👷 **Declare placeholders and actions which depend on the client in the _action_ stage (not after action expansion)**
;;
;; 🚥 **Client effects execute first, then server effects execute after.**
;;
;; By the "action stage", I mean the vector literals we declare under a hiccup's `:on` key. "After action expansion" refers to
;; the vector literals that an action handler-fn may return. A client dependency could be the dom-node or dom-event
;; provided by replicant, as well as things like the URL bar, local-storage or an ajax request.
;;
;; Here's an example of what nexus supports, but our design _cannot_ —
;; a client-only placeholder _after_ action expansion. Here's a hiccup:

{::clerk/visibility {:result :hide}}

[:input {:type "text"
         :on   {:blur [[:change-field :email]]}}]

;; And the corresponding nexus:

{:nexus/effects      {:save            (fn [_ store path value]                         ;4
                                         (swap! store assoc-in path value))
                      :prevent-default (fn [{{:keys [dom-event]} :dispatch-data}]
                                         (.preventDefault dom-event))}
 :nexus/actions      {:change-field (fn [state id]
                                      [(when-not (get-in state [:fields id :disabled?]) ;3
                                         [:save [:fields id] [:event.target/value]])    ;1
                                       [:prevent-default]])}                            ;2
 :nexus/placeholders {:event.target/value (fn [{:replicant/keys [dom-event]}]
                                            (some-> dom-event .-target .-value))}}

;; Our `:change-field` handler returns both (1) `[:event.target/value]` and (2) `[:prevent-default]`
;; back to nexus after action expansion. To process them, nexus would need to execute
;; in the client runtime. But the handlers also depend on (3) `state` and (4) `store`, which only have value
;; in the server runtime. So, where exactly should we execute this action? How could we fulfill
;; both requirements, client-state and server-state, without making an architectural mess?
;;
;; Here's what our solution looks like.
;; We run nexus both on the client and on the server, both using the same spec, declared in cljc.
;; To distinguish handlers which can only run on the client, we'll mark them with `^:nextjournal.offworld/client`:

{:nexus/effects      {:save            (fn [_ store path value]
                                         (swap! store assoc-in path value))
                      :prevent-default ^:🪐/client (fn [{{:keys [dom-event]} :dispatch-data}]
                                                     (.preventDefault dom-event))}
 :nexus/actions      {:change-field (fn [state id value] [(when-not (get-in state [:fields id :disabled?])
                                                            [:save [:fields id] value])])}
 :nexus/placeholders {:event.target/value ^:🪐/client (fn [{:replicant/keys [dom-event]}]
                                                        (some-> dom-event .-target .-value))}}

;; And here's the new hiccup:

[:input {:type "text"
         :on   {:blur [[:change-field :email [:event.target/value]]
                       [:prevent-default]]}}]

{::clerk/visibility {:result :show}}

;; The hiccup is more verbose, but this could be good verbosity.
;; It's explicit about what client mechanisms it uses, while still abstracting
;; the domain logic.
;; Thus, we form an opinion: reading & mutating client-side state
;; is best done in colocation with the triggering event,
;; not buried inside handlers or helper functions.
;;
;; Let's see how we can implement this.
;; Here's our hiccup, rendered to an html string on the server, and pushed to the DOM using datastar.
;; _No need to read all this_, I'll deconstruct it below.
;;
;; ```
;; <input type="text" data-on:blur="@get(\"/replicant-dispatch\", {payload: {actions: nextjournal.offworld.divert(evt, \"[[:change-field :email [:event.target/value]]\n[:prevent-default]]\")}})" />
;; ```
;;
;; The `data-on:blur` attribute is a datastar expression. Here's a readable view of it:

{::clerk/visibility {:code :hide}}

(clerk/code {::clerk/opts {:language "js"}} "@get(\"/replicant-dispatch\",
     {payload:
      {actions:
       nextjournal.offworld.divert(
        nextjournal.offworld.user_nexus,
        evt,
        \"[[:change-field :email [:event.target/value]]
          [:prevent-default]]\")}})")

{::clerk/visibility {:code :show}}

;; The expression contains a string literal (`[[:change-field ...]]`) representing the actions.
;; The rest is boilerplate. Datastar binds the dom-event to `evt`, which is all we need to
;; fulfill the clientside state requirement. We also pass along the nexus, which the user makes available
;; using a registry function: `(🪐/register-nexus! my-nexus)`
;;
;; Then, it invokes `divert` - this inspects the nexus to decide which actions to dispatch to the client vs. the server.

(defn divert
  [nexus dom-event actions-str]
  (let [actions        (edn/read-string actions-str)
        dispatch-data  (replicant/build-event-map dom-event)
        select-client  #(into {} (filter (comp :🪐/client meta val)) %)
        client-action? (select-client
                        (merge (:nexus/effects nexus)
                               (:nexus/actions nexus)))
        client-nexus   (update nexus :nexus/placeholders select-client)
        server-actions (vec (remove (comp client-action? first) actions))
        client-actions (vec (filter (comp client-action? first) actions))]
    (nexus/dispatch client-nexus (atom nil) dispatch-data client-actions)
    (pr-str (nexus/interpolate client-nexus dispatch-data server-actions))))

;; It dispatches client-actions immediately — note the `(atom nil)`, meaning we provide no
;; replicant store to the client-actions.

;; Then, it uses the client-only placeholder functions to interpolate the server-actions.
;; Finally, it returns the new server-actions to datastar, which sends them to the server within a GET request.
;; This interpolation is how we achieve declarative actions which use state from the client,
;; but then execute on the server.
;; In effect, we have added a few new steps to our event/render loop:
;;
;; 0.1. Dispatch client-actions:         nexus    (within a datastar expression on the client)

;; 0.2. Pre-interpolate server-actions:  nexus    (within a datastar expression on the client)
;; 1. Dispatch actions:                  datastar (via GET request)
;; 2. Interpolation, expansion, render:  nexus    (on the server)
;; 3. Mutate DOM:                        datastar (via SSE handler)
;;
;; Open questions:
;; - What about clientside async effects?
;; - How should interceptors work?
;; - Do we need this much serialization? Maybe there's a cleaner way.
;; - We run replicant & nexus on the client, but we only use a few features. Can we still get a tiny bundle (e.g. cljs-lite)?

;; ### Concept B: Datastar, but only for morphing (not interactivity)
;;
;; Similar to Concept A, but pre-interpolation & dispatch are implemented as a nexus interceptor.
;; In that case, we wouldn't attach datastar expressions to our html at all. We'd just use replicant/nexus on
;; the frontend. The interceptor would interpolate the actions, then abort the actions on the client, and
;; send them to the server via GET request. From there, nexus would process the actions, replicant would render the html,
;; and datastar would patch the DOM.
;;
;; - We'd bundle replicant & nexus for the frontend. Could this still be a tiny bundle somehow?
;; - Should we selectively dispatch actions to client/server? E.g. `[:div {:on {:click [[:client/do-this] [:server/do-that]]}}`
;;   - Could have problems with ordering, racing & coordinating two state atoms.
;;
;; ### Concept C: Push actions through a datastar signal
;;
;; `<script data-effect="nexus.core.dispatch($server_initiated_actions)" />`
;;
;; ## Can we render some parts on client, some on server?
;; fast initial page load with SSR
;; switch to CSR?

;; ## Can we run replicant "commands" on the client?
;; For instance, if we provide a server.js artifact, which executes these in SCI or CLJS.
;; - Limited effects.
;; - Local-store persistence?
;; ### Replicant on SCI.
;; Replicant can run within clerk's SCI environment.
;; Maybe the user's "server" could run within SCI, alongside it.

(clerk/eval-cljs '(do
                    (js/console.log "running in the browser")
                    (replicant.string/render [:h1 "Hello from SCI"])))

;; ## Can Clerk's viewers be built with replicant/datastar?
;; ### "Hello world" in datastar
;; We modified clerk to include datastar in the browser runtime
;; ([72eb20d1](https://github.com/nextjournal/tabla/commit/72eb20d1cd98097ef31fe52752beac2084b7e224)).
;; Here's datastar's "hello world" running in clerk:

(clerk/html "<button data-on:click=\"alert('I’m sorry, Dave. I’m afraid I can’t do that.')\">
    Open the pod bay doors, HAL.
</button>")

;; ### Server-side rendering via the `:transform-fn`
;; Here we use replicant to render an html string on the JVM, then display it within a reagent component.
;; So far, this only produces static html. There isn't any wiring in place for the component
;; to communicate with your server. No signals, events, commands, etc.

^{::clerk/viewer viewers/replicant-ssr}
[:div "Hello from Replicant!"]

;; Replicant naively passes on any keys in your hiccup as html attributes.
;; That makes it straightforward to express datastar html using hiccup.

^{::clerk/viewer viewers/replicant-ssr}
[:button {:data-on:click "alert('Datastar, via replicant!')"} "Hello from Replicant!"]

;; ## [#B] `nested-grid` reagent component - can we use in clerk?

;; ## [#B] `nested-grid` reagent component - use in ductile?

;; ## [#B] Can Ductile be built with replicant/datastar/SSR/morphing?
;; - replicant or datastar?

;; ## What types of clientside actions would we want to do, even in SSR mode?

;; ## What do we name this project?

;; - [offworld](https://bladerunner.fandom.com/wiki/off-world_colonies)

;; ## What are our inspirations?
;; - https://www.inkandswitch.com/
;; - https://mas.to/@scottjenson@social.coop/115707072046013892
;; - https://observablehq.com/d/6d8a31a315f4ad94
;; - https://krcah.com/building-sse-endpoint-in-clojure-ring-core-async
;; - https://medium.com/@ianster/the-microlith-and-a-simple-plan-e8b168dafd9e
;; - https://github.com/starfederation/datastar/issues/482
