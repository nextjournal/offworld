(ns nextjournal.table.sketches
  (:require
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.table.ui :as ui]
   [nextjournal.table.clerk-viewers :as viewers]))

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

;; ## How can datastar & replicant share responsibilities?

;; ## Can we render some parts on client, some on server?
;; fast initial page load with SSR
;; switch to CSR?

;; ## Can we run replicant "commands" on the client?
;; For instance, if we provide a backend.js artifact, which executes these in SCI or CLJS.
;; - Limited effects.
;; - Local-store persistence?
;; ### Replicant on SCI.
;; Replicant can run within clerk's SCI environment.
;; Maybe the user's "backend" could run within SCI, alongside it.

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
;; to communicate with your backend. No signals, events, commands, etc.

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

;; ## What do we name this project?

;; - [off-world](https://bladerunner.fandom.com/wiki/Off-world_colonies)

;; ## What are our inspirations?
;; - https://www.inkandswitch.com/
;; - https://mas.to/@scottjenson@social.coop/115707072046013892
;; - https://observablehq.com/d/6d8a31a315f4ad94
;; - https://krcah.com/building-sse-endpoint-in-clojure-ring-core-async
;; - https://medium.com/@ianster/the-microlith-and-a-simple-plan-e8b168dafd9e
;; - https://github.com/starfederation/datastar/issues/482
