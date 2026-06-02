```clojure
(ns nextjournal.table.sketches
  {:nextjournal.clerk/error-on-missing-vars :off
   :nextjournal.clerk/toc true}
  (:require
   [clojure.string :as str]
   [clojure.edn :as edn]
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.offworld.demo.ui :as ui]
   [nextjournal.offworld.demo.clerk-viewers :as viewers]
   [nextjournal.offworld :as 🪐]
   [nextjournal.baseline :as k]
   [nextjournal.offworld.demo.biz :as biz]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [replicant.core :as replicant]))

^{::clerk/visibility {:code :hide :result :hide}} (def state nil)
^{::clerk/visibility {:code :hide :result :hide}} (defn render-a [& _])
^{::clerk/visibility {:code :hide :result :hide}} (defn render-b [& _])
^{::clerk/visibility {:code :hide :result :hide}} (defn render-c [& _])
^{::clerk/visibility {:code :hide :result :hide}} (defn render-x [& _])
^{::clerk/visibility {:code :hide :result :hide}} (defn render-y [& _])
^{::clerk/visibility {:code :hide :result :hide}} (defn render-z [& _])
{::clerk/visibility {:result :hide}}
```

# Sketches with replicant, datastar & tables

Server-side rendering is interesting[^tomorrow-to-yesterday].
But with SSR, you always need a server. What if the client lags or goes offline?

On the other hand, rich clients can be nice[^re-frame].
But these can be slow and complex. We end up inventing capabilities which our server already has.

A codebase is often designed around one paradigm or the other. Switching or combining can daunt us
with big rewrites and hacks. On the other hand, we use Clojure. If we just simplify our fundamental patterns,
can we get this flexibility for "free"?

## Quickstart.
To demonstrate our thinking, this repo includes a clj webserver and a cljs client.

- First, launch a repl: `clj -M:dev`.
- Then, evaluate `(user/start!)`. This runs clerk, a webserver and a client build.
- Then, visit [http://localhost:8000](http://localhost:8000). This demonstrates the rich client.
- To run the same app in server-side-rendering mode, visit [http://localhost:8000?ssr=true](http://localhost:8000?ssr=true).

## What does ductile's `omnibox` look like when built from replicant?
### Web Standards
Part of the replicant experience is stripping away our re-invented wheels and relying on modern web standards.
Often, for the cost of some DOM-local state and a few imperative calls,
we can refer our design to a very broad standard.
This reduces cognitive overhead for developers, both human and AI.

#### HTML Popover API ([baseline 2025](https://caniuse.com/wf-popover))
Ductile's omnibox stored hide/show state in a local atom[^ductile-local-atom], passing it among child components.
It used a global event listener[^ductile-listener] to close the popover when the user clicks outside it.
The browser can handle the same hide/show state transition, along with the detailed click behavior, using the popover api.

[^ductile-local-atom]: [omnibox.cljs#L47](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L47)
[^ductile-listener]: [omnibox.cljs#L69](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L69)

#### CSS Anchor Positioning ([baseline 2026](https://caniuse.com/css-anchor-positioning))
Ductile's omnibox used dom walking, listeners and react hooks[^walking-listeners-hooks]
to synchronize the popover's position as users scroll through its parent container(s).
For the cost of passing an id around[^id-passing],
anchor positioning lets us express all this behavior declaratively, with just a few
css properties[^css-props].

[^walking-listeners-hooks]: [omnibox.cljs#L72](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L72)
[^id-passing]: [offworld/.../omnibox.cljc#L164](https://github.com/nextjournal/offworld/blob/c6743a6387577832592fee301b0960e6d1df56bd/src/nextjournal/table/ui/omnibox.cljc#L164)
[^css-props]: [offworld/.../omnibox.cljc#L97](https://github.com/nextjournal/offworld/blob/c6743a6387577832592fee301b0960e6d1df56bd/src/nextjournal/table/ui/omnibox.cljc#L97)

#### Lightweight focus management
Ductile's omnibox modeled the state of a "selection"[^select-state], letting the user select different choices using the arrow keys[^select-arrows], and imperatively scrolling to the choice item[^scroll-into-view]. The browser can manage this UX for us[^nexus-focus], handling any scrolling automatically and complying with accessibility standards.

[^select-state]: [ductile/.../omnibox.cljs#L513](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L513)
[^select-arrows]: [ductile/.../omnibox.cljs#L394](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L394)
[^scroll-into-view]: [ductile/.../omnibox.cljs#L321](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/components/omnibox.cljs#L321)
[^nexus-focus]: [offworld/.../nexus.cljc#L105](https://github.com/nextjournal/offworld/blob/c6743a6387577832592fee301b0960e6d1df56bd/src/nextjournal/table/nexus.cljc#L105)

## Can UI elements still have a "lifecycle?"
In reagent, we often use React's `ref`[^react-ref] to access the html element that our component is rendering.

For instance, re-com's nested-grid stores a reference to its dom-node in a local reagent atom[^rc-grid-ref], then uses a reagent lifecycle method to react to that node's scroll-state and flex-sizing. This reaction necessary to determine the right virtualization "window", while still remaining lightweight and compatible by leaving scroll and sizing "uncontrolled".

We can still model this behavior, even without a component abstraction. Replicant passes the target dom-node to placeholder- and action-handlers. That's enough for the most basic features - for instance, our nested-grid demo gets the same scroll-state using placeholders, and then updates `::k/local` state to trigger a re-render [^ng-scroll].

We might also need to do actor-like messaging, where one element can observe another element's browser-local state, and act on it. But again, we can skip any component model and just use the DOM. We make a dom-node addressable the old fashioned way, by rendering an html id. For instance, our mapbox render-fn declares the id of one element within the action handler of another[^component-id-passing]. This way, our hiccup describes how the `:button` "acts" on the mapbox `:div`, and the only abstraction we need is a let-binding.

Using the html id, the action handler gets the node from a standard DOM query, then uses a `recall` function to get the mapbox js object which it can "act" on, via method calls. We made this `recall` possible by dispatching an action when the mapbox `:div` enters the DOM, calling an injected `remember` function[^mapbox-recall]. This is straight out of replicant's playbook[^replicant-js-interop]. The `remember` function puts our mapbox object into a global WeakMap, keyed by the dom-node. The WeakMap's contract subordinates the object's lifecycle to the dom-node's lifecycle[^mdn-weakmap]. When that node is removed from the dom, the corresponding mapbox object gets GC'd. For instance, when the user clicks away to another tab, we "unmount" the div, and the mapbox gets destroyed. When the user returns, we "mount" the div and construct a whole new mapbox object.

This gives us a minimal "component lifecycle" pattern. Since we articulate it using portable data (action-vectors), the pattern works in the exact same way, whether in SSR, CSR or hybrid offline-mode. In SSR mode, datastar's morph does the "mount" and "unmount" behavior, while in CSR, replicant's vdom reconciler does it.

If we need to persist some of our "component's" underlying state across mounts and unmounts (or, with SSR, across sessions), we can dispatch an action that both stores the essential state and mutates the object[^mapbox-reinit]. That way, we can reinitialize the "component" to its last-known state whenever it mounts. This depends on our `::k/local` pattern, where we assign a unique `::k/path` to each meaningful "component" of our UI. That means, to build a persistent "component" with javascript batteries included, all you need is a path and an id. That sounds like two things, but essentially they are one, since the id can be a pure derivation of the path.

Id passing lets us express the concept of "this", without adopting any special architecture. It keeps our mental model close to the browser, enabling synergy with modern browser APIs.

[^ng-scroll]: [nested_grid.cljs#L73](https://github.com/nextjournal/offworld/blob/85ac5efcf30bb1c233f6178d06dcdce21b6b0a9f/src/nextjournal/table/ui/nested_grid.cljc#L73)
[^component-id-passing]: [mapbox.cljc#L39](https://github.com/nextjournal/offworld/blob/8ca56413dbfe03f30f099f26c1d54227379a5945/src/nextjournal/offworld/demo/mapbox.cljc#L39)
[^mapbox-recall]: [mapbox.cljc#L23](https://github.com/nextjournal/offworld/blob/8ca56413dbfe03f30f099f26c1d54227379a5945/src/nextjournal/offworld/demo/mapbox.cljc#L23)
[^replicant-js-interop]: https://replicant.fun/tutorials/javascript-interop/
[^mdn-weakmap]: "Once... a key has been collected, its corresponding values... [become] candidates for garbage collection as well" - [WeakMap (MDN)](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap)
[^rc-grid-ref]: [re-com/../nested_grid.cljs#L393](https://github.com/day8/re-com/blob/6be4763003aa4c990ddf00cad5fdc13a6a8d512f/src/re_com/nested_grid.cljs#L393)
[^rc-dropdown-client-rect]: [re-com/.../nested_grid.cljs#L552](https://github.com/day8/re-com/blob/6be4763003aa4c990ddf00cad5fdc13a6a8d512f/src/re_com/dropdown.cljs#L552)
[^data-ref]: [https://data-star.dev/reference/attributes#data-ref](https://data-star.dev/reference/attributes#data-ref)
[^replicant-remember]: [https://replicant.fun/life-cycle-hooks/#memory](https://replicant.fun/life-cycle-hooks/#memory)
[^remember-mapbox]: [mapbox.cljc#L12](https://github.com/nextjournal/offworld/blob/47087ce83306e78e0be424f29f63c46f4dfc74e0/src/nextjournal/offworld/demo/mapbox.cljc#L12)
[^mapbox-pass-id]: [mapbox.cljc#L36](https://github.com/nextjournal/offworld/blob/47087ce83306e78e0be424f29f63c46f4dfc74e0/src/nextjournal/offworld/demo/mapbox.cljc#L36)
[^mapbox-reinit]: [mapbox.cljc#L40](https://github.com/nextjournal/offworld/blob/8ca56413dbfe03f30f099f26c1d54227379a5945/src/nextjournal/offworld/demo/mapbox.cljc#L40)

## How do we organize all the state a render-fn requires?
I'll focus the discussion around a chain of replicant render-fns:

- **`alert`**: Basic UI. A pure function of its argument.
- **`hover-alert`**: Stateful UI. It needs to get a `hover?` value from somewhere, and dispatch some action to change it.
- **`station-panel`**: Business UI. It depends on your business domain. It translates business semantics into UI semantics.
- **`main-view`**: Business UI containing other business UI.

### Pure render-fn: Okay?

Here's a simple render-fn I'll use in the following example.
It doesn't try to use any "local" or "domain" state.
It takes non-namespaced keys which represent its UI "contract".

```clojure
(defn alert [{:keys [level label style]}]
  [:span {:style (merge {:color (case level :warn :orange :error :red nil)}
                        style)}
   label])
```

### Prop Drilling: Bad? 
Here's a naive implementation using prop drilling, illustrating the badness alleged by Albert[^albert]:

```clojure
(defn hover-alert [{:keys [level label hover? hover-path]}]
  [:div {:on {:mouse-over [[:save hover-path true]]
              :mouse-out  [[:save hover-path false]]}}
   (alert {:level level
           :label label
           :style (when hover? {:border "2px dashed black"})})])

(defn station-panel
  [{:as       state
    :biz/keys [problems station]
    :keys     [hover-states hover-states-path]}]
  (for [{:keys [id title severity]}
        (filter problems (comp #{station} :station))]
    (hover-alert
     {:label      title
      :level      severity
      :hover?     (get hover-states id)
      :hover-path (conj hover-states-path id)})))

(defn main-view [state]
  (for [k [:dresden :hanover]]
    (station-panel
     {:hover-states      (get-in state [:biz/panel :ui/hover-alerts])
      :hover-states-path [:biz/panel :ui/hover-alerts]
      :biz/problems      (:biz/problems state)
      :biz/station       k})))
```

Some issues come to mind. None of these are dealbreakers, but they express the frustration of prop-drilling.

**`hover-alert`**:
- With `hover?`, we get a value along with its path. That makes it straightforward to model a change, using actions.
- We save values in the system, but what happens if the UI ancestor `main-view` gets unmounted at runtime?
  Those values will sit in the system forever!

**`station-panel`**:
- We get two business values passed in, and we post-process & destructure them into useable values.
  - Why is that the responsibility of this render-fn? 
  - Even if we extracted the operation to a helper-fn, why call it here? Why not in `main-view`?
  - Not that one place is better than the other, but simply having to choose comes with an engineering cost.
- We pass a map to `hover-alert`, effectively translating business terms to UI terms.
  - Except, these two hover keys *aren't* translating, they're just a mechanism.
  - Why put domain and UI in the same map? Which is which? This feels inarticulate.

**`main-view`**:
- Now we face a confusing api. We're trying to render a problem-list. Why do we need `:hover-states` and `:hover-states-path`? 
  - We can guess how these relate to our "problem-list", but it's becoming less obvious.
- On the other hand, the function signature shows me what's required - no side-channels or hidden apis.
  - It's hard to see *why* it needs these args, but at least the UI is guaranteed to work.
  - This makes it nice to work with a repl, as well — the arguments *are* the scope.
- We finally see an explicit value for `:hover-states-path`.
  - When the user hovers, part of the top-level replicant system definitely changes value... under that exact path... somewhere. Probably.
  - In reality, child render-fns are free to use any path they want.
  - This is less explicit than it looks. It's all held together by a loose convention.
- Why did we put our choice of path into *this* render-fn? Seems arbitrary.
- We have to assume our `state` argument contains the same subtree as the top-level state.
  - That means we're re-expressing the shape of the replicant state across an ever-growing set of callsites. Not very DRY.
- What if there's another `main-view` somewhere? How do we know our `:hover-states-path` isn't getting reused?
- A different dev wrote this render-fn. They prefer to write inline getters.
  - Now, to understand this function's requirements we have to read its entire body.

### Prop Drilling: Are we sure it's bad?
💬 **mk** The more I think and read about it, the less I'm convinced prop drilling is really so bad. Here's a slightly different take of the examples above: 

(I think it might be easier to discuss this with a better real-world use case.)

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/code
 "(defn hover-alert [config state state-key]
  (let [hover? (get state state-key)]
    [:div {:on {:mouse-over [[:effects/save state-key true]]
                :mouse-out  [[:effects/save state-key false]]}
           :style     (when hover? {:border \"2px dashed black\"})}
     (alert config)]))")

^{::clerk/visibility {:code :hide}}
(clerk/code
 "(defn station-panel
  [{:as       state
    :biz/keys [problems problem-area]}]
  (for [{:keys [id title severity]}
        (filter problems (comp #{problem-area} :area))]
    (hover-alert
     {:label      title
      :level      severity}
          state
      [::hover-alert id])))")

^{::clerk/visibility {:code :hide}}
(clerk/code
 "(defn main-view [state]
  (for [problem-area [:cars :trucks]]
    (station-panel
     {:biz/problems      (:biz/problems state)
      :biz/problem-area  problem-area})))")
```

Some ideas to adress some of the problems above:
- Combine the state access with the saving instead of needing to pass in both a path and the value of the state
- Use the simplest state key possible, treat it as an opaque key:
  - This can often be a namespaced keyword
  - Use a vector only when you will have multiple instances of the same component with conflicting keys on it, e.g. when wanting to support multiple tables with per-column omniboxes on the same page

### Global State: Bad?

This is more concise, but we get less observability.

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/code
 "(defn hover-alert [{:keys [id level label]}]
   (let [hover? (get @system [::hover-alert id])]
     [:div {:on {:mouse-over [[:save [::hover-alert id] true]]
                 :mouse-out  [[:save [::hover-alert id] false]]}}
      (alert {:level level
              :label label
              :style (when hover? {:border \"2px dashed black\"})})]))")
^{::clerk/visibility {:code :hide}}
(clerk/code "(defn station-panel
  [{:as   state
    :keys [station]}]
  (for [{:keys [id title severity]}
        (biz/get-problems {:station station})]
    (hover-alert
     {:id    id
      :label title
      :level severity})))")
^{::clerk/visibility {:code :hide}}
(clerk/code "(defn main-view [_]
  (for [k [:dresden :hanover]]
    (station-panel {:station k})))")
{:nextjournal.clerk/visibility {:code :show}}	
```

`hover-alert`:
- This builds a path to access some global state.
- `id` had better be globally unique, or else we'll have collisions.
- How do we show the same state in two places, but with unique hover behavior?
  - We'd have to encode the unique UI location into `id`. Otherwise, the hover state will be duplicated.
  - Of course this is possible, but the problem is that we have to make a choice.
  - How reliably "local" the state is depends on how disciplined we are in calling `hover-alert`.

`biz-problems-list`:
- This uses a re-frame-like registration to get a domain value, using the key `::problems`.
  - What parts of state does this come from?
  - It probably looks up another registration and filters the result. And so on. What are these?

`main-view`: 
- What if we remove this? What parts of state will become unused?
- What if we add this to another app? What registrations will we need to make available?
- If we change the api for `::problems`, how will we know that this render-fn is affected?
- I just got an error: "undefined query handler: `::macguffin`". Why does `main-view` need that? I don't see a call anywhere.

### Prop-drilling or global-state?

With prop-drilling, the explicitness and repl-friendliness can truly benefit a team.
They make UI very easy to reason about. On the other hand, changing the UI tree causes a mess of re-drilling,
slowing the team down. Also, without any standard convention, it's up to our devs to "invent" the 
means to get and update values.

Can we prop-drill in a way that doesn't make messes, and requires less creativity?

With global state, render-fns are only explicit about their direct deps, not the deps of their children.
This can feel organized, but it's more implicit. Nevertheless, most of the Clojure community has settled
on this pattern. With re-frame, we tend to solve the observability problems using
tooling. For instance, re-frame-10x provides a "visual repl".

Can we build observability tools that are more explicit and repl-friendly?

### Separating concerns
I see a pattern emerging. There are three distinct ways that a render-fn can model state, each with a defining constraint:

- **Configuration**:   the caller is responsible for providing this value and modeling its change and locality.
- **Local state**:     like configuration, but *this* function models change and locality.
- **Business domain**: UI should *not* model its locality or change, or else we get "tar-pit"[^tar-pit] issues. Instead, its locality is global, and change is modeled by a registry of event handlers.

### Drilling responsibly
When we use pure functions, where all required values are arguments, we gain significant repl-friendliness and testability.
But "global" and "local" state patterns give organizational clarity.

With top-down rendering, we can just do both. The "global" state can just *be* one of the arguments. Clojure's immutable data structures handle this perfectly - simple and powerful.

This is, more or less, a dependency-injection pattern.

## What structure do we pass into a render-fn?
What's the ideal way to structure this "responsible drilling" pattern? 
What do we actually pass? How is the "global" part propagated?

If we separate state into three concerns, then it's up to each render-fn to recombine them.
What are the pitfalls? What if we mix up config & domain? What if we use local *as* config?

### State concept A: Pass `config` (with library keys inside)
Here's what a typical callsite looks like:
```clojure
(hover-alert
 {:level     :warn
  :label     "Construction zone"
  ::k/path   [::k/local :stations :dresden :alerts 0]
  ::k/domain {:biz/stations [:hanover :dresden]
              :biz/problems #{{:id       0
                               :title    "Construction zone"
                               :severity :warn}}}
  ::k/local  {:stations
              {:dresden
               {:alerts {0 {:hover? true}}}}}})
```

`::k/domain` and `::k/local` come directly out of the replicant system-state. `::k/path` comes from the caller. Here's a more detailed walkthrough:

#### Receiving "local" state
```clojure
(defn hover-alert [{:keys    [level label]
                    ::k/keys [path local]}]
  (let [{:keys [hover?]} (get local path)]
    [:div {:on {:mouse-over [[:save (conj path :hover?) true]]
                :mouse-out  [[:save (conj path :hover?) false]]}}
     (alert {:level level
             :label label
             :style (when hover? {:border "2px dashed black"})})]))
```

Here we wrap the basic `alert` with local state management. When the user mouses over the wrapper div, our `:save` action updates a value within replicant's system state, under `::k/local`. This causes replicant to re-render. It calls `hover-alert` again, and this time it destructures `hover?` to pass a style map the the `alert` fn.

#### Injecting "local" state, querying "domain" state

```clojure
^{::clerk/visibility {:code :hide :result :show}}
(clerk/code
 "(defn station-panel [{:as     state
                       :keys    [station]
                       ::k/keys [domain local path]}]
   (for [{:keys [id title severity]}
         (biz/get-problems domain {:station station})]
     (hover-alert {:label    title
                   :level    severity
                   ::k/path  (into path [:alerts id])
                   ::k/local local})))")
```

Here's a "business" component, translating domain semantics into UI semantics.
It receives `path` and `local` from yet another caller, extending `path` and passing both along.

We also receive another injection: `domain`.
We use a helper from our business logic to get a value out of it.

Here's one here's one level higher in our UI call chain.
This `main-view` just returns its children, with injections:

```clojure
(defn main-view' [{:as      state
                   ::k/keys [domain local path]
                   :or      {path []}}]
  (station-panel
   {:station   :dresden
    ::k/local  local
    ::k/path   (into path [:stations :trucks])
    ::k/domain domain})
  (station-panel
   {:station   :hanover
    ::k/local  local
    ::k/path   (into path [:stations :trains])
    ::k/domain domain}))
```

#### Helpers
We can make this pattern more regular and concise with helper-fns.
For instance, here's that last component rewritten. 

```clojure
(defn main-view [state]
  (for [k [:dresden :hanover]]
    (station-panel
     (k/+ state [:stations k]
       {:station k}))))
```

### State concept B: Pass a `domain`, (with lib keys inside)
Similar to concept A, but inside-out:

```clojure
(hover-alert
 {::k/config    {:label "Construction zone" :level :warn}
  ::k/local     {:stations
                 {:dresden
                  {:alerts {0 {:hover? true}}}}}
  ::k/path      [::k/local :stations :dresden :alerts 0]
  :biz/problems #{{:id 0 :severity :warn :title "Construction zone"}}
  :biz/stations [:hanover :dresden]})
```
#### Goes against Replicant ergonomics
This won't feel like a "replicant" render-fn, since we don't destructure top-level keys to decide how to render. Instead, we get them from `::k/config`. Rewriting a "stateless" fn (like `alert`) into one that uses local state (like `hover-alert`) is complicated, since we have to destructure the arg differently.

#### Query the domain: Easy! 
Domain keys are top-level, not in a subtree. Just call `(get-something state)` to query the domain.

### State concept C: Merge everything together
Here we achieve separation in a different way:

- plain keys represent `config`
- ns-keys represent `domain`
- flattened vector keys represent `local`

This way, a callsite looks like:

```clojure
(hover-alert
 {::k/path      [::k/local :stations :dresden :alerts 0]
  :biz/problems #{{:id 0 :severity :warn :title "Construction zone"}}
  :biz/stations [:hanover :dresden]
  :label        "Construction zone"
  :level        :warn
  [:stations :dresden :alerts 0] {:hover? true}})
```

#### Uses `clojure.core` as an implicit DSL
For example, reagent has special behavior when you pass a vector: `[my-component]`.
Reagent code doesn't *name* this behavior, you just have to know. 
We'd do a similar thing, building special behavior for namespaced keys and vectors.

I like how we're close to the "bare meta."
Namespaces *are* domains, vectors *are* paths. There's formal elegance to that.

But this might frustrate a more pragmatic dev.
We can't just look at Clojure and see clojure any more.
There's an unarticulated meaning behind the structure.
If the separation of concerns is so clear, why not just name them with keys (i.e. Concept A)?
Maybe that's less pretentious.

#### Hard to handle dev sloppiness
I think we'd need the "special behavior" above, because if devs don't follow our 3-type convention, 
we get strange consequences. Here are some accidents I can foresee:

- **Write a domain query that gets a vector:**  
`(defq get-alert [station] (get m [:stations station]))`
*Oops, I used `get` instead of `get-in`. And since I named my paths after my domain, 
that path exists! My domain-getter now returns local-state. That's nonsense!*
  
- **Destructure domain keys from the argument:**  
`(defn render-station [state] [:span (:biz/station state)])`
*The key is right there in the arg, so I don't need a getter. That's okay, but I lost observability. My teammates can't trace this dependency, so they forgot to fulfil it!*

- **Filter out some domain keys:**  
`(defn render-x [m] (map render-y (filter-vals my-pred state)))`
*Oops, I meant to update the config map, but I removed half the keys from our global domain, and passed that to every descendent. I'd rather the domain be `nil` than this degenerate value!*

- **Pass a namespaced key within config:**  
`(defn render-x [m] (render-y (assoc m :biz/vehicle :plane)))`
*Oops, now `render-y` sees a degenerate domain. It builds an action-vector based on planes, but the global domain is set to trucks. My action handler just tried to update wings on a truck. That's a CQRS violation!*

- **Pass the global domain in a brittle way:**  
`(defn render-x [{:biz/keys [vehicle station]}] (render-y {:biz/vehicle vehicle :biz/station station :color :blue}))`
*Oops, my team quietly added a third key, `:biz/route`, to the global domain. My `render-x` used to be correct, but now it passes down a degerate domain.*

- **Leak the config to a descendent:**  
```clojure
(defn render-w [state] (render-x (assoc state :class :underline)))
(defn render-x [state] [:div {:class class} (render-y state)])
(defn render-y [state] (render-z state))
(defn render-z [state] [:div {:class class}])
```
*Oops, I forgot to remove the config-key `:class` before calling `render-y`. `render-y` doesn't use that key anyway, so it's fine... right? But `render-z` does use that key. I only meant to declare one layer of underlined content, but now there are two!*

We usually don't want to pass the same config to every descendent, but it's hard not to.
When `config` is an open set of unqualified keys, how do we know what to dissoc?
The only predicate we can use is `namespace`:

```clojure
(defn render-x [{:as state :keys [class]}]
  (let [domain? (comp namespace key)
        path?   (comp sequential? key)
        drill?  (some-fn domain? path?)]
    [:div {:class class}
    (render-y (into {} (filter drill?) state))]))
```

Every render-fn will need to do exactly this job. We can extract the work to a helper, but the question remains: Why take care to separate these values everywhere? Why couldn't we place them separately in the first place?

### State concept D: Pass a combined `stem`
```clojure
(hover-alert
 {:label   "Construction zone"
  :level   :warn
  ::k/path [::k/local :stations :dresden 0]
  ::k/stem {:biz/stations [:hanover :dresden]
            :biz/problems #{{:id 0 :severity :warn :title "Construction zone"}}
            ::k/local     {:stations
                           {:dresden
                            {:alerts {0 {:hover? true}}}}}}})
```

This looks more complicated, but it makes for nice ergonomics. 

- `::k/stem`[^stem-name]:
  - It's just the value of the replicant (sy)stem, with no restructuring.
  - It's not called "domain" because it contains more than that.
  - For "domain" queries, just pass `stem`: `(biz/get-problems stem)`
  - For "local" state, use a library getter: `(k/local state)` or `(k/local stem)`
- `::k/path`: 
  - Paths must begin with `::k/local`. Our passing helper can handle that for us, so we only declare the meaningful part of the path.
  - We don't "accumulate" paths by default. The user can just do `conj`, or we'll provide another helper.

### State concept E: Close over the replicant state
Maybe we're overdoing it with "responsible drilling". Our render-fns could all just close over the replicant state to achieve our "global" pattern.
This is "impure," but it's just one symbol, well-known to library users.

This ensures the value we're querying truly is the "global" state,
since there aren't any callers that might tamper with it.

Same goes for "local" state, which we store within this "global" value.
We still pass a path into any render-fn that needs some "local" state.

We can still substitute the value in tests, using `with-redefs`.

```clojure
(defn hover-alert [{:keys    [level label path]
                    ::k/keys [path]}]
  (let [{:keys [hover?]} (get k/*stem* path)]
    [:div {:on {:mouse-over [[:save (conj path :hover?) true]]
                :mouse-out  [[:save (conj path :hover?) false]]}}
     (alert {:level level
             :label label
             :style (when hover? {:border "2px dashed black"})})]))

(defn station-panel [{:as      state
                      :keys    [station]
                      ::k/keys [path]}]
  (for [{:keys [id title severity]}
        (biz/get-problems k/*stem* {:station station})]
    (hover-alert {:label    title
                  :level    severity
                  ::k/path  (into path [:alerts id])})))
```

## How do we model a render-fn's "local" state?
We're considering ways to reserve a subtree of the system state for a particular callsite of a render-fn, similar to react's `use-state`. The smallest thing we can provide is a **path**.

This path could be fully automated[^membrane], but that's too magical for our taste. Instead, we'll declare the path explicitly within a render-fn's callsite:

`(ui/main-view {::k/path [:biz/vehicle :ui/panel 25]})`

A path joins over business domain, ui config and ui nesting. It says: "*this* business fact, displayed *this* way, in *this* location." In practice, paths could be more abstract, but I think they carry this essential meaning.

### Problem: what if our element unmounts? How do we "garbage collect"[^albert] the "local" state?
Replicant has `:replicant/on-unmount`[^on-unmount], 
and datastar has a `data-on-remove`[^data-on-remove] plugin.

💬 **mk**: I'm also not yet convinced component UI state sticking around in the system map after being unmounted is a problem. If it turns out to be, we can also clean up the state on load[^on-load-hooks]

### Problem: The caller has to "take care to build up a path"[^albert]
I agree - this could lead to bikeshedding. If our devs "invent" a new path for every callsite,
we'll have two structures: the essential tree of render calls, and a second, redundant structure of "local" paths.

### Path Concept A: Fully explicit paths
When we call a render-fn, we decide its entire path:

`(defn render-a [state] (render-b assoc state ::k/path [:biz/vehicle :ui/inspector 25]))`

#### Is this PLOP?
Only the current render-fn should have access to "local" state. 
But if that state is not relevant elsewhere, then why, in the first degree of elsewhere, i.e. the caller,
do we take care to "name" it using an explicit path?
### Path Concept B: Get the head passed in, append an explicit tail.
This way, paths "accumulate" as the tree of render-fn calls gets deeper.

```clojure
(defn render-a [{:as state :k/keys [path]}]
  (render-b (assoc state ::k/path (concat path [:extra :stuff]))))
```

Although more complex to implement, we get a nice property:
the structure of "local" state mirrors the call tree of render-fns.
This ensures every render-fn gets to access a unique location, without collisions.

We shouldn't really care about the shape of "local" state, because:
- Each location should be accessible only to a single render-fn
- It's accessbile via an opaque "path" that gets passed in
- Its value should be ephemeral

All this makes the task of choosing a path feel less "inventive".

### Path Concept C: Prepend every path with a library key: `[::k/local ...]`
This helps us avoid collisions while drilling and querying.
If we also use Path Concept B, then the mental cost is low, since we never have to write the entire path by hand.
A library getter can help resolve ambiguities: `(k/local state)` can do the perfect destructuring for us.

### Conclusion
We could have Concepts A, B and C within the same app:
Use A for more central pieces, and B for pieces that are more nested and instanced.
I think Concept C is preferable in all cases.

## How do we translate our business domain into UI?
### Caching
I suggest we *don't* provide any built-in mechanism to cache queries. In my work on re-frame,
I saw all the issues caused when users get caching by default, especially the issue of domain/ui coupling[^flows-advanced].
Our solution was a "flow"[^flows], which lets the user implement the caching themselves via lifecycle methods.
Crucially, these lifecycle methods did *not* depend on the state of the UI. Instead, they
were pure functions of the app-db. The "cache" was not some out-of-band mechanism,
but instead, it simply stored its latest value within the same app-db. We could then extract that value
using a pure function, since the flow itself executed its effects after every event,
regardless of what the UI was doing.
As a result, library users could implement a "cached" value in the few cases where it's needed, 
with total control and almost no magic. 

This clean pattern depended on "re-frame-time"[^re-frame-time], where events get handled in a single queue,
effects take place on a global app-db singleton, and views only change as a reaction to app-db.
Replicant's top-down approach gives us an even simpler model than "re-frame-time",
so I think our new stack can enable something as nice as "flows" for a-la-carte caching.

### Composability
If one query can invoke another query, then we can express our domain using DRY, reusable pieces.
The current demo uses `nextjournal.offworld.demo.ui.holiday` to demonstrate this.

### Observability
Querying a "global" domain can make our UI code very concise. 
Render-fns can declare what they need out of the business domain,
without the caller needing to micromanage their dependencies. 
But the downside is decreased observability.
Looking at a single render-fn, you can't see all the domain values its children will require.
Traditionally, we've solved the observability problem at the tooling level - for instance,
with re-frame-10x. There you can see what queries get invoked after each event,
and get some explanation of *why* they're invoked.

The *why* tends to include:
- What render-fn(s) depend on this query (or query-id)?
- What queries does this render-fn depend on?
- What is the value of this query?
- What queries have changed value?
- What event led this query to change value?
- What upstream queries changed value, in order for this query to change value?

How can we offer this observability in a minimal, repl-friendly way?

#### Tracing
A `trace` fn should be available, to fill in the detail we gave up when choosing
to use a global-state query pattern.

```clojure
^{::clerk/visibility {:code :hide :result :hide}}
(def mermaid-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn [value]
                 (when value
                   [nextjournal.clerk.render/with-d3-require {:package ["mermaid@8.14/dist/mermaid.js"]}
                    (fn [mermaid]
                      [:div {:ref (fn [el] (when el
                                             (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])]))})
```

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/with-viewer mermaid-viewer
  "flowchart LR
  	:icon --> :holiday-mode
    :icon --> :day
	:day  --> :season")
```

Note that the query handler for `:icon` does some control flow, using `when`.
The `:day` query is only done *sometimes*, depending on what's in the state.
By tracing at runtime, we see the graph of all queries that are *actually* done.
Here's an invocation of the same query that doesn't depend on `:day`:

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/with-viewer mermaid-viewer
  "flowchart LR
  	:icon --> :holiday-mode")
```

#### Tracing render-functions

The eager evaluation model of top-down rendering means we can also trace a render-fn at runtime.
Thus we have a minimal, repl-driven tool to address our global-state pain point:

- *If I add or delete this render-fn, what parts of domain state will be affected?*

This tracing also provides useful error messages. Say we register a new query
which depends on an *unregistered* query, `:day-missing`:

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/code
"(k/register! :icon-error
  #(when (k/q % :holiday-mode?)
     (day->icon (k/q % :day-missing))))")

^{::clerk/visibility {:code :hide}}
(clerk/code "(k/trace {::path {:to {:holiday-mode? true}}} ::icon-error)")

^{::clerk/visibility {:code :hide}}
(clerk/code
"Missing query: :nextjournal.offworld.demo.ui.holiday/day-missing
   :nextjournal.offworld.demo.ui.holiday/icon-error
    └─ :nextjournal.offworld.demo.ui.holiday/day-missing")
```

This tells us exactly what handler-fn couldn't be called, as well as a history of dependencies
to illustrate exactly why our query needs to call it.

We should also trace what render-fns were called to produce this error[^todo].

#### Tracing "local" state[^todo]
Our `k/+` function narrows the UI path, which we can also trace at runtime.
We should include this when tracing a render-fn, to know what subtrees 
of `::k/local` are being used by the render-fn and its children.

#### Tracing static dependencies[^todo]
Looking back at the query handler registration, we added metadata to some
of the handler-fns: 

```clojure
^{::clerk/visibility {:result :hide}}
{::k/deps [:day :holiday-mode?]}
```

With this, our tooling can do static analysis on our codebase, answering a complementary question:

- *Given this query or render-fn, tell me **all possible** queries this could invoke.*

We might want to improve the concision with a macro, similar to deframe[^deframe-event].

[^deframe-event]: [deframe events in ductile](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/src/ductile/ui/deframe.cljs#L245)

> 🤔 **mk**
>
> I'm wondering if we're getting ahead of ourselves to think about caching & tracing already. I feel it might be worth to put these things off until we really run into problems that drive the need for it. The replicant design seems to encourage keeping high-fidelity state out of replicant and moving it into the browser, potentically using web components. Maybe this is something worth exploring, using acutal use cases we have on ductile like the VIN input.


### Query Concept A: Minimalist registry
The examples in *State Concept A* use a `k/q` function to query the domain state.
As for modeling change, nexus can handle this - we'll just namespace our actions by the domain.
Here's what query handlers look like:

```clojure
{:nextjournal.clerk/visibility {}}
(def season->holiday {:spring :egg-day
                      :summer :bird-day
                      :fall   :squash-day
                      :winter :gift-day})

(clerk/code '(k/register! :season
  #(get-in % [:path :to :season] :spring)))

(clerk/code '(k/register! :day
  ^{::k/deps #{:season}}
  #(season->holiday (k/q % :season))))

(def day->icon {:gift-day   "🎁"
                :egg-day    "🥚"
                :bird-day   "🦃"
                :squash-day "🎃"})

(clerk/code '(k/register! :holiday-mode?
  #(get-in % [:path :to :holiday-mode?])))

(clerk/code '(k/register! :icon
  ^{::k/deps #{:day :holiday-mode?}}
  (fn [domain & {:keys [emphasize?]}]
    (when (k/q domain :holiday-mode?)
      (str (day->icon (k/q domain :day))
           (when emphasize? "!"))))))
		   
{:nextjournal.clerk/visibility {:result :show}}
```

To use one, pass a domain map to `k/q`, followed by a registered key and any extra args.

```clojure
(clerk/code '(let [domain {:path {:to {:holiday-mode? true :season :winter}}}]
  (k/q domain :icon)))
```

You can also pass an entire system state. The handler will get passed the `::k/domain` subtree, 
followed by any extra args.

```clojure
(clerk/code '(let [state {::k/domain {:path {:to {:holiday-mode? true :season :winter}}}}]
  (k/q state :icon {:emphasize? true})))
```

On the call side, these look like re-frame. However, following the replicant philosophy,
we're exploring how to strip them back down to essentials:
### Query Concept B: Plain functions, with a trace helper.
We could avoid the query DSL and registry, and just use regular pure fns to "get" a business value.
For a static dependency graph, we can just declare the required fns in metadata.
For a dynamic dependency graph, we can wrap our function body with a trace helper, or use a macro
to wrap it for us.

This way, our query pattern is truly first-class in clojure. No more special tooling 
for looking up registry keys.

### Query Concept C: Queries all the way down
Arguably, in top-down rendering, render-fns *are* queries.
And we'd like to have tracing on render-fns too.
Could we provide a single library macro, `k/defn`, that can declare both render-fns and queries?

## Can we design an API for data grids in clerk & ductile?
## Can we build a `nested-grid` in cljs using replicant's "top-down" UI model?

Commit [7ecd60b](https://github.com/nextjournal/offworld/commit/7ecd60b/)
adds a component based on re-com's [`nested-grid`](https://re-com.day8.com.au/#/nested-grid).
`nested-grid` helps users explore large datasets by rendering a scrollable rectangular "window" of the data.
The search algorithm is the same, and the render-fn is stripped down and converted to
use replicant-flavored hiccup.
Here's a picture of the worst-case performance of this stripped-down component:

```clojure
(clerk/image "http://localhost:8000/img/d6f5737-cljs-render-perf.png")
```

It's rendering a "window" of 4,000 cells, out of a dataset of 250,000.
The render-fn, including nested-grid's search algorithm, takes 30ms.
Once replicant gets the hiccup from the render-fn, it mutates the DOM, adding 4,000
totally new elements. Replicant takes 500ms. Then the browser takes over, taking 200ms
to draw the new DOM.
Notes:
- With replicant we control the render call explicitly, so it's easy to set up profiling scenarios like this one.
- For a while, the render-fn was getting called twice. I didn't realize that returning two effect-vectors from
  the action handler caused two separate swaps and two render calls ([a40100a](https://github.com/nextjournal/offworld/commit/a40100a/)).
- So far, this doesn't seem much slower than the original reagent version.
## Datastar "morphing" grid demo
Visit `localhost:8000?ssr=true` to run nested-grid with server-side rendering. Still evaluating this,
but it seems to work nicely so far.
## What grid features can we offer the user?
- ordering?
- "computed" columns?
- filtering
- labels
- show-header?
- similar to `re-com.table-filter`
- Summary cells (powered by SSR) ([e.g.](https://observablehq.com/d/6d8a31a315f4ad94))
## How can datastar, replicant & nexus delegate responsibilities in SSR mode?
We'd like to build an application from a single clojure expression,
which can run either fully on the client, or with a client/server pattern, using server-side-rendering.
Client-only mode seems simple enough - we just build a conventional replicant/nexus app.
SSR mode is trickier to get right.
To articulate a full event/render loop, we need to cover these responsibilities:
1) Dispatch actions
1) Interpolate actions (requires: dom-event)
1) Expand actions      (requires: dom-event, system-state)
1) Interpolate effects (requires: dom-event, system-state)
1) Batch effects
1) Execute effects     (requires: dom-event, system-state)
1) Render hiccup
1) Render html
1) Mutate DOM
A few requirements constrain our choice of framework & runtime to which we can delegate each step:
- The _dom-event_ only has value in the client.
- The _system-state_ could be stored anywhere, but we should constrain it to either client or server —
  otherwise, we'd coordinate two _system-states_, undermining the simplicity advantage of SSR.
- Replicant & nexus can run on the server, on the client, or _within_ datastar expressions.
- Datastar expressions can only run on the client.
### SSR Concept A: Replicant SSR with Datastar at the edges
Intuitively, I feel the cleanest design would be:
- Dispatch actions: datastar          (first on the client, then on the server.)
- Everything else:  replicant & nexus (on the server)
- Mutate DOM:       datastar          (on the client)

We can achieve this if we follow one convention, and one operational rule:

👷 **Declare placeholders and actions which depend on the client in the _action_ stage (not after action expansion)**

🚥 **Client effects execute first, then server effects execute after.**

By the "action stage", I mean the vector literals we declare under a hiccup's `:on` key. "After action expansion" refers to
the vector literals that an action handler-fn may return. A client dependency could be the dom-node or dom-event
provided by replicant, as well as things like the URL bar, local-storage or an ajax request.
Here's an example of what nexus supports, but our design _cannot_ —
a client-only placeholder _after_ action expansion. Here's a hiccup:

```clojure
{::clerk/visibility {:result :hide}}
```

```clojure
[:input {:type "text"
         :on   {:blur [[:change-field :email]]}}]
```

And the corresponding nexus:

```clojure
{:nexus/effects      {:save            (fn [_ system path value]                         ;4
                                         (swap! system assoc-in path value))
                      :prevent-default (fn [{{:keys [dom-event]} :dispatch-data}]
                                         (.preventDefault dom-event))}
 :nexus/actions      {:change-field (fn [state id]
                                      [(when-not (get-in state [:fields id :disabled?]) ;3
                                         [:save [:fields id] [:event.target/value]])    ;1
                                       [:prevent-default]])}                            ;2
 :nexus/placeholders {:event.target/value (fn [{:replicant/keys [dom-event]}]
                                            (some-> dom-event .-target .-value))}}
```

Our `:change-field` handler returns both (1) `[:event.target/value]` and (2) `[:prevent-default]`
back to nexus after action expansion. To process them, nexus would need to execute
in the client runtime. But the handlers also depend on (3) `state` and (4) `system`, which only have value
in the server runtime. So, where exactly should we execute this action? How could we fulfill
both requirements, client-state and server-state, without making an architectural mess?
Here's what our solution looks like.
We run nexus both on the client and on the server, both using the same spec, declared in cljc.
To distinguish handlers which can only run on the client, we'll mark them with `^:nextjournal.offworld/client`:

```clojure
{:nexus/effects      {:save            (fn [_ store path value]
                                         (swap! store assoc-in path value))
:prevent-default ^::🪐/client (fn [{{:keys [dom-event]} :dispatch-data}]
                                                     (.preventDefault dom-event))}
 :nexus/actions      {:change-field (fn [state id value] [(when-not (get-in state [:fields id :disabled?])
                                                            [:save [:fields id] value])])}
 :nexus/placeholders {:event.target/value ^::🪐/client (fn [{:replicant/keys [dom-event]}]
                                                        (some-> dom-event .-target .-value))}}
```

And here's the new hiccup:

```clojure
[:input {:type "text"
         :on   {:blur [[:change-field :email [:event.target/value]]
                       [:prevent-default]]}}]
```

```clojure
{::clerk/visibility {:result :show}}
```

The hiccup is more verbose, but this could be good verbosity.
It's explicit about what client mechanisms it uses, while still abstracting
the domain logic.
Thus, we form an opinion: reading & mutating client-side state
is best done in colocation with the triggering event,
not buried inside handlers or helper functions.
Let's see how we can implement this.
Here's our hiccup, rendered to an html string on the server, and pushed to the DOM using datastar.
_No need to read all this_, I'll deconstruct it below.

```clojure
{::clerk/visibility {:code :hide}}
```

```clojure
(clerk/code {::clerk/opts {:language "html"}}
"<input type=\"text\" data-on:blur=\"@get(\\\"/offworld-dispatch\\\", {payload: {actions: nextjournal.offworld.divert(nextjournal.offworld.user_nexus, evt, \\\"[[:change-field :email [:event.target/value]] [:prevent-default]]\\\")}})\" />")
```

The `data-on:blur` attribute is a datastar expression. Here's a readable view of it:

```clojure
(clerk/code {::clerk/opts {:language "js"}} "@get(\"/offworld-dispatch\",
     {payload:
      {actions:
       nextjournal.offworld.divert(
        nextjournal.offworld.user_nexus,
        evt,
        \"[[:change-field :email [:event.target/value]]
          [:prevent-default]]\")}})")
```

The expression contains a string literal (`[[:change-field ...]]`) representing the actions.
The rest is boilerplate. Datastar binds the dom-event to `evt`, which is all we need to
fulfill the clientside state requirement. We also pass along the nexus, which the user makes available
using a registry function: 

```clojure
(clerk/code "(🪐/register-nexus! my-nexus)")
```

```clojure
{::clerk/visibility {:code :show}}
```

Then, it invokes `divert` - this inspects the nexus to decide which actions to dispatch to the client vs. the server.

```clojure
^{::clerk/visibility {:result :hide}}
(defn divert
  [nexus dom-event actions-str]
  (let [actions        (edn/read-string actions-str)
        dispatch-data  (replicant/build-event-map dom-event)
        select-client  #(into {} (filter (comp ::🪐/client meta val)) %)
        client-action? (select-client
                        (merge (:nexus/effects nexus)
                               (:nexus/actions nexus)))
        client-nexus   (update nexus :nexus/placeholders select-client)
        server-actions (vec (remove (comp client-action? first) actions))
        client-actions (vec (filter (comp client-action? first) actions))]
    (nexus/dispatch client-nexus (atom nil) dispatch-data client-actions)
    (pr-str (nexus/interpolate client-nexus dispatch-data server-actions))))
```

It dispatches client-actions immediately — note the `(atom nil)`, meaning we provide no
replicant system to the client-actions.

Then, it uses the client-only placeholder functions to interpolate the server-actions.
Finally, it returns the new server-actions to datastar, which sends them to the server within a GET request.
This interpolation is how we achieve declarative actions which use state from the client,
but then execute on the server.
In effect, we have added a few new steps to our event/render loop:

0.1. Dispatch client-actions:         nexus    (within a datastar expression on the client)

0.2. Pre-interpolate server-actions:  nexus    (within a datastar expression on the client)
1. Dispatch actions:                  datastar (via GET request)
2. Interpolation, expansion, render:  nexus    (on the server)
3. Mutate DOM:                        datastar (via SSE handler)

Open questions:
- What about clientside async effects?
- How should interceptors work?
- Do we need this much serialization? Maybe there's a cleaner way.
- We run replicant & nexus on the client, but we only use a few features.
  Can we still get a tiny bundle (e.g. cljs-lite)?

### SSR Concept B: Nexus interceptors?
Similar to Concept A, but pre-interpolation & dispatch are implemented as a nexus interceptor.
In that case, we wouldn't attach datastar expressions to our html at all. We'd just use replicant/nexus on
the frontend. The interceptor would interpolate the actions, then abort the actions on the client, and
send them to the server via GET request. From there, nexus would process the actions, replicant would render the html,
and datastar would patch the DOM.
- We'd bundle replicant & nexus for the frontend. Could this still be a tiny bundle somehow?
- Should we selectively dispatch actions to client/server? E.g. `[:div {:on {:click [[:client/do-this] [:server/do-that]]}}`
  - Could have problems with ordering, racing & coordinating two state atoms.
### Concept C: Return client-effects from server-actions
Here's a new addition to what we do in Concept A:

- Pre-interpolate actions on the client 🌎
  - Expand client actions on the client 🌎
	- Execute client effects 🌎
  - Expand server actions on the server 🪐
	- Execute server effects 🪐
    - NEW✨: Send client effects back the client, via SSE 🌎

We can achieve this with minimal infra. Just add this to the static HTML:

`<script data-effect="nexus.core.dispatch($server_initiated_actions)" />`

After expanding actions on the server, offworld intercepts the return value. 
It separates out any effects marked `^::🪐client`, serializes them and pushes an SSE `data-patch-signals` event, updating the `$server_initiated_actions` signal.

#### Problem: we can't send identical events, since `data-effect` only runs when the signal changes.
Yes, but we can salt the signal value with a gensym.

#### Problem: how sync/async is this?
We'll have to look into exact consequences[^todo].
What if an action causes both a re-render and a server-initiated-action?
Will they race?
What effects can we recommend to use this way?
An alert or toast is probably okay?

### ~~SSR Concept D: Make some server values available to client actions.~~
Update: I think this is a bad idea. See: [Problem: is this pointless?](#problem:-is-this-pointless?)

While we don't cache all of our system state in the client, it might be useful to 
cache a few values. These values can summarize our most recent system state for the purpose of making
decisions. Specifically, we can declare a domain query[^query] as a placeholder:

```clojure
(defn randomize-button [state]
  [:button {:on {:click [[::randomize
                          [:event/key-modifiers]
                          [::k/q ::season]]]}}
   "Randomize (shift-click to reset)"])
```

I've demonstrated this with a new action, tied to a "Randomize" button in the top-right corner.
It uses placeholders to require state both from the client and from the server.

We don't push these values to the client in a reactive or on-demand fashion.
Instead, we rely on replicant's top-down model to simply push all the values before each render,
using a datastar-patch-signals event[^patch-signals-sse].

How do we know what values to push? Again, we rely on the simplicity of top-down rendering.
Our render-fns yield static data structures, so we can simply walk them to discover the relevant placeholders.

[^patch-signals-sse]: [main.clj#L68](https://github.com/nextjournal/offworld/blob/55a9175c3a80afc4976a6a114a4ec2053c103c52/src/nextjournal/table/main.clj#L68)

#### Problem: Is this a bad feature?
So far, I'm not sure. Is this over-designed? I know the current implementation is convoluted,
but what about the feature? Does it complement the rest, or is it a footgun?

#### Problem: Is this pointless?
The placeholder derives from a static "snapshot" of the system state. But, so does 
the entire hiccup returned from our render-fn. We don't need a placeholder, because
we can simply *call* `k/q`. Then our "summary" value will get serialized into the datastar expression.
No need for any extra wiring.

```clojure
(defn randomize-button [state]
  [:button {:on {:click [[::randomize
                          [:event/key-modifiers]
                          (k/q state ::season)]]}}
   "Randomize (shift-click to reset)"])
```

[^query]: See [#domain-queries](#domain-queries)

### SSR Concept E: Return server-effects from client-actions

- Pre-interpolate actions on the client 🌎
  - Separate client-actions from server-actions
  - Expand client actions on the client 🌎
	- Execute client effects 🌎
    - NEW✨: Group server-effects into the server-actions, via SSE 🌎
  - Expand server actions on the server 🪐
	- Execute server effects 🪐

Here's an example:

```clojure
(nxr/register-action! ::randomize
  ^::🪐/client
  (fn [_ key-mods season]
    (let [path        [::k/domain ::path :to :season]
          reset?      (contains? (set key-mods) :shift)
          rand-season (first (rand-nth (seq (dissoc season->holiday season))))
          new-season  (if reset? :spring rand-season)]
      [[:browser/alert "A new holiday is here!"]
       ^::🪐/ssr
       [:effects/save path new-season]])))

(defn randomize-button [state]
  [:button {:on {:click [[::randomize
                          [:event/key-modifiers]
                          (k/q state ::season)]]}}
   "Randomize (shift-click to reset)"])
```

We see the usual `^::🪐/client` meta, indicating that this handler must always be executed on the client. The handler receives two inputs: `key-mods`, derived from client state via a placeholder, and `season`, derived from the server's system-state at render time. Now, here's the new part:

The handler returns two effects. Offworld separates these, sending the first to the client and the second to the server.

I'm not confident I've found the perfect way to indicate this behavior, but here's an attempt:

1. `^::🪐/client` can be attached to an action *handler*, indicating it must be executed on the client.

2. `^::🪐/ssr` can be attached to an action *vector*, indicating it must be executed on the server, when in SSR mode, and on the client when in client-only mode.

> 🤔 **mk**
>
> I'm wondering if an alternative to needing both `^::🪐/client` and `^::🪐/ssr` metadata could be to move the client-only effects to a separate map, with both the client and the server nexus knowing about its keys.
>
> Could you then just use these keys to decide what needs to be run where, elimating the need to flag individual action/effect vectors with `^::🪐/ssr`? 

Worth a try. The declarations would be more readable. I think I see how we can do it.

in SSR mode, we have two different maps. The client needs to know about the server-map. So that map needs reader conditionals for any handlers that use clj-only features.

In CSR mode, we merge the server map into the client map. Those same reader conditionals come in handy, so we can re-implement clj-only handlers in cljs as needed.

What about the nexus registry? Will offworld need to take over that responsibility from nexus? That would be unfortunate. Here's where the meta keys are still more reasonable and ergonomic, I think.

#### Problem: can effects be actions? Do we need to wrap them?
Maybe we'll need to wrap them:
```clojure
[[::🪐/server-effect [:effects/save '...]]]

(nxr/register-action! ::🪐/server-effect
 (fn [_ effect] effect))
```

Our helper-fn `divert` can handle this wrapping, so it doesn't impact the library user.


### SSR concept F: Client placeholder, Server action, Client effect
- Pre-interpolate on the client 🌎
- Push actions to the server 🌎->🪐
- Expand all actions on the server 🪐
  - Push client effects to the client (with placeholders) 🪐->🌎
	- Interpolate client effects 🌎
	- Execute client effects 🌎
	- Acknowledge client effects 🌎->🪐
  - Execute server effects 🪐

Here we accept that we can't pass a js-only value through the full chain from actions to effects. Instead, we'd use placeholders in the effects we send back to the client.

#### Benefit: less serialization?
Similar to lightweight-labs/feather[^feather-uuids], could we just pass uuids for dispatch and acknowledgement?

#### Problem: two roundtrips per dispatch sounds buggy

## Can we render some parts on the server, and some on the client?
fast initial page load with SSR
switch to CSR?
### Offline mode
Consider this user story:

- Load the page in SSR mode.
- Go offline.
- Click a button. Your UI changes.
- Go online.
- Your change is still there, next to a new change made by your coworker.

For instance, with Ductile, users want to board a ship without internet, collect data by scanning truck license plates, then disembark and continue using the app.

This requires some kind of "sync" operation on our app state. I find it easy to think of this as a git tree.
When a user goes offline, it's like they're forking onto a "local" branch. When they go back online, they have to merge back into the latest "main" branch, fixing any conflicts that arise. A conflict might be two users scanning the same truck. Or, user A updates that scan result, while user B deletes it. The best way to merge often depends on the use-case. For scanning trucks, maybe the user should decide, or maybe some logic on the server should handle it.

One problem: our app state only exists on the server. In that case, how do we create our "fork"?

#### Offline concept A: re-render some inner elements using replicant

Maybe we can sidestep the complexities of offline-sync by separating out a "slice" of the UI.
Then, our truck scanner can work offline without us re-engineering the entire app, 
or synchronizing the entire system.

When the user goes offline, we highlight a few "offline-capable" elements, greying out the rest.
Such an element doesn't need our *entire* system state to do its job, just a few subtrees. 
We wrap a hiccup to make it "offline-capable"[^offline-a1]. For this, we declare:

- The render-fn that should keep running when offline.
- A set of `:select-paths`, to subtrees of the server's `system`.
- `::k/config`: the current named args passed into the render-fn.
  - These won't change as long as the user is offline, since the caller is "disabled".

Our server returns html as usual, but it stores the "selected" subtrees as an edn blob
within a data attribute[^offline-a2]. This way, the necessary state gets "cached" on the client,
before the user goes offline.

When the user goes offline, the client reads the blob to initialize a "temp" replicant `system`[^offline-a3].
Based on that system, it renders our UI "slice" directly to our wrapper div.
When the user triggers an action, nexus handles it in CSR mode, applying effects to the "temp" system[^offline-a4].
This way, our UI "slice" reacts "optimistically", showing the same updates, as
if the server were still responding. 

We also pass down a snapshot of the last known server state, in case we need to inform the user of what 
state is "confirmed" on the server and what's still "optimistic."

At the same time, we separate out the server-bound actions & effects, just like in SSR mode.
But, instead of sending them off to the server, we store them in a temporary `action-log`[^offline-a5].
When the user goes back online, we send the `action-log` and the `system` to the server.
This conveys all the user's offline activity as a single intent. Then, it's up to 
the server to reconcile this intent with its system state. For the demo, we simply dispatch 
the entire `action-log`. But, since we've separated the intent from any implementation, 
we're free to build a more sophisticated sync algorithm - for instance, a differential sync[^diff-sync]
could reconcile between multiple users touching the same state.

In any case, after reconciliation, the server pushes the latest html tree.
Our UI "slice" gets morphed back into a datastar-driven element, the client stops calling replicant, 
and we're back to our usual SSR render loop.

[^offline-a1]: [ui.cljc#L26](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/table/ui.cljc#L26)
[^offline-a2]: [offline.clj#L19](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld/offline.clj#L19)
[^offline-a3]: [offline.cljs#L29](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld/offline.cljs#L29)
[^offline-a4]: [offworld.cljc#L157](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld.cljc#L157)
[^offline-a5]: [offworld.cljc#L156](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld.cljc#L156)
[^diff-sync]: https://blog.ndk.io/why-csp-matters1.html

##### Problem: how do we know what paths the component needs?
The local path is easy to deal with.
What about queries? A query can look anywhere within the state.
We do know exactly what queries a render-fn needs to make.
But to rigorously sync queries, we'd be implementing IVM - complicated.

##### Idea: Should we cache queries, too?
We could treat some queries as "static" while offline - instead of running the query-fn, 
just return a snapshot value.

##### Idea: declare path guardrails for queries
Just like we declare a static `::k/deps`, on a query[^static-deps], indicating the required sub-queries,
we could declare a `::k/paths`[^static-paths], promising to use only certain subtrees of the state.
Then, we could infer an element's required state from the union of every query's paths.
This could be a nice 80/20 solution, bottoming out on the simplest queries, which are analagous
to re-frame's "layer-2 extractors"[^re-frame-l2].

Since static trace is available at runtime[^render-trace], we might get away with a zero-config macro:

```clojure
^{::clerk/visibility {:code :hide}}
(clerk/code "(🌠/offline-capable (truck-scanner (k/+ state my-path {:config true})))")
```

[^static-deps]: [scan.cljc#L38](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld/demo/scan.cljc#L38)
[^static-paths]: [scan.cljc#L28](https://github.com/nextjournal/offworld/blob/4d0c404b94bcd812e046f63eecbe7efad5c2a906/src/nextjournal/offworld/demo/scan.cljc#L28)
[^re-frame-l2]: https://day8.github.io/re-frame/subscriptions/#the-four-layers
[^render-trace]: [baseline.cljc#L129](https://github.com/nextjournal/offworld/blob/main/src/nextjournal/baseline.cljc#L129)

##### Tradeoff: this requires replicant on the client. We're back to a thick bundle.

## Can we run replicant "commands" on the client?
For instance, if we provide a server.js artifact, which executes these in SCI or CLJS.
- Limited effects.
- Local-store persistence?
### Replicant on SCI.
Replicant can run within clerk's SCI environment.
Maybe the user's "server" could run within SCI, alongside it.

```clojure
(clerk/eval-cljs '(do
                    (js/console.log "running in the browser")
                    (replicant.string/render [:h1 "Hello from SCI"])))
```

## Can Clerk's viewers be built with replicant/datastar?
### "Hello world" in datastar
We modified clerk to include datastar in the browser runtime
([72eb20d1](https://github.com/nextjournal/offworld/commit/72eb20d1cd98097ef31fe52752beac2084b7e224)).
Here's datastar's "hello world" running in clerk:

```clojure
(clerk/html "<button data-on:click=\"alert('I’m sorry, Dave. I’m afraid I can’t do that.')\">
    Open the pod bay doors, HAL.
</button>")
```

### Server-side rendering via the `:transform-fn`
Here we use replicant to render an html string on the JVM, then display it within a reagent component.
So far, this only produces static html. There isn't any wiring in place for the component
to communicate with your server. No signals, events, commands, etc.

```clojure
^{::clerk/viewer viewers/replicant-ssr}
[:div "Hello from Replicant!"]
```

Replicant naively passes on any keys in your hiccup as html attributes.
That makes it straightforward to express datastar html using hiccup.

```clojure
^{::clerk/viewer viewers/replicant-ssr}
[:button {:data-on:click "alert('Datastar, via replicant!')"} "Hello from Replicant!"]
```

## What would a gradual migration of ductile look like?
Could we make a feature-flag/alpha-flag using ductile's router,
to switch on offworld one route/view at a time?
## [#B] `nested-grid` reagent component - can we use in clerk?

Here's a basic viewer for nested grids.
It doesn't virtualize yet.
First, we'll define a function to call for each cell.
It uses its paths within a structure of nested rows and columns to
transform the base `data` into a summary value:

```clojure
(defn get-join-str [data row-path col-path]
  (str/join (map data (into row-path col-path))))
```

And here's the full table spec:

```clojure
^{::clerk/viewer viewers/nested-grid}
{:row-depth     2
 :col-depth     2
 :nested-cols   [:a [:b] [:c]]
 :nested-rows   [:x [:y] [:z]]
 :data          {:a "A" :b "B" :c "C" :x "X" :y "Y" :z "Z"}
 :cell-viewer   `get-join-str
 :row-viewer    `peek
 :col-viewer    `peek
 :corner-viewer `vector
 :data-shape    :nested-grid}
```

## [#B] `nested-grid` reagent component - use in ductile?
## [#B] Can Ductile be built with replicant/datastar/SSR/morphing?
- replicant or datastar?

## What types of clientside actions would we want to do, even in SSR mode?
- animations
- focus an element
- scroll to an element
- measuring element position/dimensions
- positioning
- local storage, indexed storage, indexedDB


## What do we name this project?

- [offworld](https://bladerunner.fandom.com/wiki/off-world_colonies)

### Ideas for subordinate names:

- **stem**: "What comes from something else? Stem."
- **baseline**: State management.

## What are our inspirations?
- https://www.inkandswitch.com/
- https://mas.to/@scottjenson@social.coop/115707072046013892
- https://observablehq.com/d/6d8a31a315f4ad94
- https://krcah.com/building-sse-endpoint-in-clojure-ring-core-async
- https://medium.com/@ianster/the-microlith-and-a-simple-plan-e8b168dafd9e
- https://github.com/starfederation/datastar/issues/482

[^injection]: [Wikipedia: Dependency Injection](https://en.wikipedia.org/wiki/Dependency_injection)
[^k-local]: See `k/local` in the [following section](#domain-state).
[^albert]: [Albert's notes: "Replican't"](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/notes/albert/replicant.md?plain=1#L16)
[^flows-advanced]: [Flows: advanced topics](https://day8.github.io/re-frame/flows-advanced-topics)
[^flows]: [Re-frame: Flows](https://day8.github.io/re-frame/Flows)
[^todo]: TODO: not implemented yet.
[^tar-pit]: [Out of the Tar Pit (Moseley & Marks, 2006)](https://www.semanticscholar.org/paper/Out-of-the-Tar-Pit-Moseley-Marks/41dc590506528e9f9d7650c235b718014836a39d)
[^membrane]: See: [Membrane UI](https://github.com/phronmophobic/membrane)
[^on-load-hooks]: > I’ve often had an “on-load” style hook for pages that trigger when the user navigates to the page from another page, like you’re describing. This is indeed a good place to do any necessary cleanup of transient state, initiate data fetch etc. You could also have an “on-leave” style hook that can trigger when someone leaves a page for another, but I find it’s better to use onload to set up/clear the necessary state. -- @cjohansen
[^on-unmount]: [replicant.fun/life-cycle-hooks](https://replicant.fun/life-cycle-hooks/)
[^data-on-remove]: [threadgold.nz/demos/data-on-remove](https://threadgold.nz/demos/data-on-remove)
[^tomorrow-to-yesterday]: David Yang: [From Tomorrow Back to Yesterday: A Tale of Two Web Architectures](https://www.youtube.com/watch?v=8W6Lr1hRgXo)
[^re-frame]: For instance, [Re-Frame](https://day8.github.io/re-frame/)
[^re-frame-time]: See: [re-frame time](https://github.com/day8/re-frame/blob/d430576ce036f97e736f2fc0f9ddec39cbedb2a1/docs/on-dynamics.md#re-frame-time)
[^stem-name]: Quoted from Blade Runner 2049's [baseline test](https://gist.github.com/JuneKelly/57b1acd4234409917d44eb90c88d7804#file-baselinetest-txt-L149)
[^react-ref]: [React: referencing values with refs](https://react.dev/learn/referencing-values-with-refs)
[^feather-uuids]: https://inv.nadeko.net/8W6Lr1hRgXo?t=1064
