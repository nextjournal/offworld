(ns nextjournal.table.sketches
  (:require
   [nextjournal.clerk :as clerk]
   [replicant.string :as rstr]
   [nextjournal.table.ui :as ui]))

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

;; ## Replicant virtual grid demo - standalone
;; - stress test for top-down rendering?

;; ## Datastar "morphing" grid demo - standalone repo

;; ## What grid features can we offer the user?
;; - ordering?
;; - "computed" columns?
;; - filtering
;; - labels
;; - show-header?
;; - similar to `re-com.table-filter`
;; - Summary cells (powered by SSR) [[https://observablehq.com/d/6d8a31a315f4ad94][e.g.]]

;; ## How can datastar & replicant share responsibilities?

(clerk/html "<button data-on:click=\"alert('I’m sorry, Dave. I’m afraid I can’t do that.')\">
    Open the pod bay doors, HAL.
</button>")

;; ## Can we render some parts on client, some on server?
;; fast initial page load with SSR
;; switch to CSR?

;; ## Can we run replicant "commands" on the client?
;; For instance, if we provide a backend.js artifact, which executes these in SCI or CLJS.
;; - Limited effects.
;; - Local-store persistence?

;; ## Can Clerk's viewers be built with replicant/datastar?
;; ### Naive server-side rendering via the `:transform-fn`
;; Here we use replicant to render an html string on the JVM, then display it within a reagent component.

(def replicant-ssr-viewer
  {:transform-fn (clerk/update-val (comp clerk/html rstr/render))})

^{::clerk/viewer replicant-ssr-viewer}
[:div {:data-on-click (pr-str [[::alert "Clicked!"]])}
 "Hello from Replicant!"]

;; ## [#B] `nested-grid` reagent component - can we use in clerk?

;; ## [#B] `nested-grid` reagent component - use in ductile?

;; ## [#B] Can Ductile be built with replicant/datastar/SSR/morphing?
;; - replicant or datastar?

;; ## What do we name this project?

;; ## What are our inspirations?
;; - https://www.inkandswitch.com/
;; - https://mas.to/@scottjenson@social.coop/115707072046013892
;; - https://observablehq.com/d/6d8a31a315f4ad94

