(ns nextjournal.table.sketches
  (:require
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.table.ui :as ui]
   [nextjournal.table.clerk-viewers :as viewers]))

;; # Sketches with replicant, datastar & tables
;; ## what does a full-featured dropdown (e.g. from ductile) look like when built from replicant?

;; ## Can we still use dom watchers like "Resize"?
;; replace the react functional ref pattern with replicant's :remember

;; ## how do we organize the "path" of a UI component?
;; - Need to pass down values and their conj'ed up paths
;; - "I just want some component local state" - now all of my parents must take care to build up a path
;; - Every value that might be changed needs its corresponding path passed down as well
;;   - `value` & `path`
;; - Need to garbage collect transient state on DOM element unmount
;;  (There are no components, thus no component lifecycle. Must hook into DOM node lifecycle)

;; ## Can we design an API for data grids in clerk & ductile?

;; ## Replicant virtual grid demo
;; - stress test for top-down rendering?

;; ## Datastar "morphing" grid demo
;; We modified clerk to include datastar in the browser runtime
;; ([72eb20d1](https://github.com/nextjournal/tabla/commit/72eb20d1cd98097ef31fe52752beac2084b7e224)).
;; Here's datastar's "hello world" running in clerk:

(clerk/html "<button data-on:click=\"alert('I’m sorry, Dave. I’m afraid I can’t do that.')\">
    Open the pod bay doors, HAL.
</button>")

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
