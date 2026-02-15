```clojure
(ns nextjournal.table.sketches
	{:nextjournal.clerk/error-on-missing-vars :off}
  (:require
   [clojure.edn :as edn]
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.table.ui :as ui]
   [nextjournal.table.clerk-viewers :as viewers]
   [nextjournal.offworld :as 🪐]
   [nextjournal.baseline :as k]
   [nexus.core :as nexus]
   [nexus.registry :as nxr]
   [replicant.core :as replicant]))
```

# Sketches with replicant, datastar & tables
Some research questions follow, along with our findings.
To demonstrate our findings, this project includes a clj webserver and a cljs client.
To launch the server and build the client, run `user/start!`.
Then, visit `http://localhost:8000`.
Some demos implement server-side rendering. In that case, visit `http://localhost:8000?ssr=true`.

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

## Can we still use dom watchers like "Resize"?
replace the react functional ref pattern with replicant's :remember

## How do we organize the "path" of a UI component?
> - Need to pass down values and their conj'ed up paths
> - "I just want some component local state" - now all of my parents must take care to build up a path
> - Every value that might be changed needs its corresponding path passed down as well
>   - `value` & `path`
> - Need to garbage collect transient state on DOM element unmount
> (There are no components, thus no component lifecycle. Must hook into DOM node lifecycle)
>
> From [Albert's notes](https://github.com/nextjournal/ductile/blob/156bc27dba9980a0b6e8bbd4866f64f17b220ab4/notes/albert/replicant.md?plain=1#L16)

I'll focus this discussion around a chain of replicant render-fns.
These demonstrate the common "concerns" a production app has to serve:

- **`alert`**: Basic UI. A pure function of its argument.
- **`hover-alert`**: Stateful UI. It needs to get a `hover?` value from somewhere, and dispatch some action to change it.
- **`biz-problem-list`**: Business UI. It depends on your business state. It translates business semantics into UI semantics.
- **`biz-panel`**: Business UI containing other business UI.

### Pure render-fn: Okay?

Here's a simple render-fn I'll use in the following example.
It has no issues, since it doesn't try to use any "local" or "domain" state.

```clojure
(defn alert [{:keys [level label]}]
  [:span {:style {:color (case level :warn :orange :error :red nil)}}
   label])
```

### Prop Drilling: Bad? 
Here's a naive implementation using prop drilling, illustrating the badness alleged by Albert:

```clojure
(defn hover-alert [{:keys [level label hover? hover-path]}]
  [:div {:on        {:mouse-over [[:effects/save hover-path true]]
                     :mouse-out  [[:effects/save hover-path false]]}
         :style     (when hover? {:border "2px dashed black"})}
   (alert {:level level
           :label label})])

(defn biz-problem-list
  [{:as       state
    :biz/keys [problems problem-area]
    :keys     [hover-states hover-states-path]}]
  (for [{:keys [id title severity]}
        (filter problems (comp #{problem-area} :area))]
    (hover-alert
     {:label      title
      :level      severity
      :hover?     (get hover-states id)
      :hover-path (conj hover-states-path id)})))

(defn biz-panel [state]
  (for [problem-area [:cars :trucks]]
    (biz-problem-list
     {:biz/problems      (:biz/problems state)
      :biz/problem-area  problem-area
      :hover-states      (get-in state [:biz/panel :ui/hover-alerts])
      :hover-states-path [:biz/panel :ui/hover-alerts]})))
```

Some issues come to mind. None of these are dealbreakers, but they express the frustration of prop-drilling.

- `hover-alert`:
  - With `hover?`, we get a value along with its path. That makes it straightforward to model a change, using actions.
  - We save values in the system, but what happens if the UI ancestor `biz-panel` gets unmounted at runtime?
    Those values will sit in the system forever!
- `biz-problem-list`:
  - We get two business values passed in, and we post-process & destructure them into useable values.
	- Why is that the responsibility of this render-fn? 
	- Even if we extracted the operation to a helper-fn, why call it here? Why not in `biz-panel`?
	- Not that one place is better than the other, but simply having to choose comes with an engineering cost.
  - We pass a map to `hover-alert`, effectively translating business terms to UI terms.
	- Except, these two hover keys *aren't* translating, they're just a mechanism.
	- Why put domain and UI in the same map? Which is which? This feels inarticulate.
- `biz-panel`:
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
	- What if there's another `biz-panel` somewhere? How do we know our `:hover-states-path` isn't getting reused?
	- A different dev wrote this render-fn. They're not as confident writing big destructuring forms. Instead, they use inline getters.
      - Now, to understand this function's requirements we have to read its entire body.

### Global State: Bad?

This is more concise, but we get less observability.

```clojure
{:nextjournal.clerk/visibility {:code :hide}}
(clerk/code
"(defn hover-alert [{:keys [id level label]}]
  (let [hover? (get @system [::hover-alert id])]
    [:div {:on    {:mouse-over [[:effects/save [::hover-alert id] true]]
                   :mouse-out  [[:effects/save [::hover-alert id] false]]}
           :style (when hover? {:border \"2px dashed black\"})}
     (alert {:level level
             :label label})]))")

(clerk/code "(defn biz-problem-list
  [{:as   state
    :keys [problem-area]}]
  (for [{:keys [id title severity]}
        (biz/get [::problems {:filter-by {:area problem-area}}])]
    (hover-alert
     {:id    id
      :label title
      :level severity})))")

(clerk/code "(defn biz-panel [_]
  (for [problem-area [:cars :trucks]]
    (biz-problem-list {:problem-area problem-area})))")
{:nextjournal.clerk/visibility {:code :show}}	
```

- `hover-alert`:
  - This builds a path to access some global state.
  - `id` had better be globally unique, or else we'll have
  - How do we show the same state in two places, but with unique hover behavior?
    - We'd have to encode the unique UI location into `id`. Otherwise, the hover state will be duplicated.
    - Of course this is possible, but the problem is that we have to make a choice.
	- How reliably "local" the state is depends on how disciplined we are in calling `hover-alert`.

- `biz-problems-list`:
  - This uses a re-frame-like registration to get a domain value, using the key `::problems`.
    - What parts of state does this come from?
    - It probably looks up another registration and filters the result. And so on. What are these?

- `biz-panel`: 
  - Here we can't see what parts of state our child component depends on.
  - What happens if we remove this? What parts of state will become unused?
  - What if we add this to another app? What registrations will we need to make available?
  - If we change the api, or the results, of `::problems`, how will we know that this render-fn is affected?
  - I just got an error: undefined query handler. But why does my render-fn even use that query? I don't see a call anywhere.

### Conclusion:

With prop-drilling, I feel the explicitness and repl-friendliness can truly benefit a team.
They make UI very easy to reason about. On the other hand, changing the UI tree causes a mess of re-drilling,
slowing the team down.

Can we prop-drill in a way that doesn't make messes, and requires less creativity?

With global state, render-fns are only explicit about their direct deps, not the deps of their children.
This can feel more organized, even though it's more implicit. Most of the Clojure community has settled
on this pattern with the re-frame paradigm. With re-frame, we tend to solve the observability problems using
tooling. For instance, re-frame-10x returns us to "repl-friendliness" by providing a custom "visual repl".
Although, 10x doesn't solve the issue of invisible dependencies. 

Can we build observability tools that are more explicit and repl-friendly?

### Solution: Drilling responsibly, with a "local" UI and a "global" domain.

- **Configuration**: values provided by the caller, who is responsible for modeling their change and locality.
- **Local state**: like configuration, but *this* function models change and locality.
- **Business domain state**: UI should *not* model its locality or change, or else we get "tar-pit"[^tar-pit] issues.
  Any UI component can observe and request a change, but those procedures should be encapsulated.

I think we can model **configuration** using top-level named arguments. **Local state** and **business domain state** can be
passed down the call chain via three namespaced keys, `::k/domain`, `::k/local` and `::k/path`.

We prevent many of the above issues simply by putting each concern in a predictable place,
with strong conventions for modeling change and locality.

[^tar-pit]: [Out of the Tar Pit (Moseley & Marks, 2006)](https://www.semanticscholar.org/paper/Out-of-the-Tar-Pit-Moseley-Marks/41dc590506528e9f9d7650c235b718014836a39d)

### Receiving "local" state
```clojure
(defn hover-alert [{:keys    [level label]
                    ::k/keys [path local]}]
  [:div {::k/cleanup [[::k/cleanup path]]
         :on         {:mouse-over [[::k/save-local (conj path :hover?) true]]
                      :mouse-out  [[::k/save-local (conj path :hover?) false]]}
         :style      (when (:hover? local) {:border "2px dashed black"})}
   (alert {:level level
           :label label})])
```

This is still a pure function, but the local state feels like a *dependency injection*[^injection].
The "injector" is simply the function caller, passing in `::k/path` and `::k/local`.
This provides a dedicated piece of state that the render-fn can evaluate and change, without
needing to hard-code any "knowledge" of its location.

#### Problem: what if our element unmounts? How do we "garbage collect"[^albert] the "local" state?
Replicant has `:replicant/on-unmount`[^on-unmount], 
and datastar has a `data-on-remove`[^data-on-remove] plugin.
Maybe we could abstract these in our render-fns, like with `:k/cleanup` above.

#### Problem: The caller has to "take care to build up a path"[^albert]
Yes, but we automate the process so it doesn't require *too* much care.
Read the following section to see how this is done.

[^on-unmount]: [replicant.fun/life-cycle-hooks](https://replicant.fun/life-cycle-hooks/)
[^data-on-remove]: [threadgold.nz/demos/data-on-remove](https://threadgold.nz/demos/data-on-remove)

### Injecting "local" state, querying "domain" state

Here's a "business" component, translating domain semantics into UI semantics.
But first, let's see how it injects the "local" state into its children:

```clojure
(defn biz-problem-list' [{:as      state
                         :keys    [problem-area]
                         ::k/keys [domain local path]
                         :or      {path []}}]
  (for [{:keys [id title severity]}
        (k/q domain :biz/problems {:area problem-area})]
    (hover-alert {:label    title
                  :level    severity
                  ::k/path  (into path [:biz/problem id])
                  ::k/local (get-in local [:biz/problem id])})))
```

See how we receive `path` and `local` from yet another caller?
We extend this `path` (using `conj`) and narrow `local` (using `get-in`) to "inject" them
into our ui component (`hover-alert`).
If we apply this pattern consistently, we will end up with a state which closely follows
the shape of our UI. But we haven't done this to our *entire* state, only a subtree stored under 
`::k/local`.

Apart from "local" state, we receive another injection: `::k/domain`.
This complements the ui-oriented `::k/local` with nonlocal, business-oriented state.
It doesn't get narrowed — we pass the exact same `::k/domain` to every render-fn.
Instead of a path, we use `k/q` to get a value out of the domain. 
`k/q` supports a query DSL similar to re-frame, with a name followed by an options map.

Here's the same component, using helper-functions to improve on concision:

```clojure
(defn biz-problem-list [{:as   state
                         :keys [problem-area]}]
  (for [{:keys [id title severity]}
        (k/q state :biz/problems {:area problem-area})]
    (hover-alert
     (k/+ state [:biz/problem id]
       {:label title
        :level severity}))))
```

`k/+` just needs the `state`, the child's local `path` and the child's ordinary arguments.
It handles destructuring, accumulating the path and all three injections.
`k/q` can be a bit more concise as well, destructuring `::k/domain` automatically.

To illustrate this even more obviously, here's one level higher in our UI call chain.
This `biz-panel` does nothing but return its children, with injections:

```clojure
(defn biz-panel' [{:as      state
                   ::k/keys [domain local path]
                   :or      {path []}}]
  (biz-problem-list
   {:problem-area :cars
    ::k/local     (get local :cars)
    ::k/path      (conj path :cars)
    ::k/domain    domain})
  (biz-problem-list
   {:problem-area :trucks
    ::k/local     (get local :trucks)
    ::k/path      (conj path :trucks)
    ::k/domain    domain}))
```

Finally, here's the same render-fn written concisely:

```
(defn biz-panel [state]
  (for [k [:cars :trucks]]
    (biz-problem-list
     (k/+ state k
       {:problem-area k}))))
```

### Domain queries
The examples above use a `k/q` function to query the domain state.
As for modeling change, nexus can handle this - we'll just namespace our actions by the domain.
Here's what query handlers look like:

```clojure
{:nextjournal.clerk/visibility {:result :hide}}
(def season->holiday {:spring :egg-day
                      :summer :bird-day
                      :fall   :squash-day
                      :winter :gift-day})

(k/register! :season
  #(get-in % [:path :to :season] :spring))

(k/register! :day
  ^{::k/deps #{:season}}
  #(season->holiday (k/q % :season)))

(def day->icon {:gift-day   "🎁"
                :egg-day    "🥚"
                :bird-day   "🦃"
                :squash-day "🎃"})

(k/register! :holiday-mode?
  #(get-in % [:path :to :holiday-mode?]))

(k/register! :icon
  ^{::k/deps #{:day :holiday-mode?}}
  (fn [domain & {:keys [emphasize?]}]
    (when (k/q domain :holiday-mode?)
      (str (day->icon (k/q domain :day))
           (when emphasize? "!")))))
{:nextjournal.clerk/visibility {:result :show}}
```

To use one, pass a domain map to `k/q`, followed by a registered key and any extra args.

```clojure
(let [domain {:path {:to {:holiday-mode? true :season :winter}}}]
  (k/q domain :icon))
```

You can also pass an entire system state. The handler will get passed the `::k/domain` subtree, 
followed by any extra args.

```clojure
(let [state {::k/domain {:path {:to {:holiday-mode? true :season :winter}}}}]
  (k/q state :icon {:emphasize? true}))
```

On the call side, these look like re-frame. However, following the replicant philosophy,
we're exploring how to strip them back down to essentials:

#### Caching
For instance, we don't cache the queries. In my work on re-frame,
I saw all the issues caused when users get caching by default, especially the issue of domain/ui coupling[^flows-advanced].
Our solution was a "flow"[^flows], which lets the user implement the caching themselves via lifecycle methods.
Crucially, these lifecycle methods did *not* depend on the state of the UI. Instead, they
were pure functions of the app-db. The "cache" was not some out-of-band mechanism,
but instead, it simply stored its latest value within the same app-db. We could then extract that value
using a pure function, since the flow itself handled every side-effect regardless of what the UI was doing.
As a result, library users could implement a "cached" value in the few cases where it's needed, 
with total control and almost no magic. 

This clean pattern depended on "re-frame-time", where events get handled in a single queue, 
the state changes as a side-effect, and views only change as a reaction to that state change.
The top-down approach gives us an even simpler concept of "re-frame-time",
so I think our new stack can provide something as nice as "flows" for a-la-carte caching.

#### Observability
Our critique above noted that these queries are concise but opaque. 
It's hard to know what state any given render-fn requires. 
Traditionally, we've solved the observability problem at the tooling level.

How can we offer this tooling in a minimal, repl-friendly way?

#### Tracing dynamic dependencies
Like subscriptions, queries are composable. One query handler can invoke another. 
To see this in action, simply run `k/trace` with the same arguments you'd pass to `k/q`: 

```clojure
(let [domain {:path {:to {:holiday-mode? true :season :winter}}}]
  (k/trace domain :icon {:emphasize? true}))
```

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
(let [domain {:path {:to {:holiday-mode? false :season :winter}}}]
  (k/trace domain :icon {:emphasize? true}))
```
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
"Missing query: :nextjournal.table.ui.holiday/day-missing
   :nextjournal.table.ui.holiday/icon-error
    └─ :nextjournal.table.ui.holiday/day-missing")
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
### Concept A: Replicant SSR with Datastar at the edges
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
"<input type=\"text\" data-on:blur=\"@get(\\\"/replicant-dispatch\\\", {payload: {actions: nextjournal.offworld.divert(nextjournal.offworld.user_nexus, evt, \\\"[[:change-field :email [:event.target/value]] [:prevent-default]]\\\")}})\" />")
```

The `data-on:blur` attribute is a datastar expression. Here's a readable view of it:

```clojure
(clerk/code {::clerk/opts {:language "js"}} "@get(\"/replicant-dispatch\",
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

### Concept B: Datastar, but only for morphing (not interactivity)
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

### ~~Concept D: Make some server values available to client actions.~~
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

### Concept E: Return server-effects from client-actions

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

#### Problem: can effects be actions? Do we need to wrap them?
Maybe we'll need to wrap them:
```clojure
[[::🪐server-effect [:effects/save '...]]]

(nxr/register-action! ::🪐/server-effect
 (fn [_ effect] effect))
```

Our helper-fn `divert` can handle this wrapping, so it doesn't impact the library user.


## Can we render some parts on client, some on server?
fast initial page load with SSR
switch to CSR?

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

## [#B] `nested-grid` reagent component - can we use in clerk?
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
