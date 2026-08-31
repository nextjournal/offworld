
# Table of Contents

-   [Use-case](#org226acb1)
-   [Thesis](#org5e5cfd8)
    -   [Quickstart: run the demo](#org4ac2786)
-   [Why](#org6c3716d)
    -   [Two paradigms, one program](#orgf198e17)
    -   [The stages are real whether or not you name them](#org9f866fb)
    -   [Offworld's SSR pipeline](#org921f2e4)
    -   [A function can't cross the wire](#org3d11541)
-   [The loop, walked](#org45c22bc)
    -   [Render — on the server](#org6007457)
    -   [In the browser](#org1fb7e3b)
    -   [Across the wire, and back](#orged836ee)
    -   [The point](#orgac6222f)
-   [The vocabulary](#org0b2dc03)
    -   [Intent](#org86e6273)
    -   [Expansion](#org666b544)
    -   [Effect](#org97ae3a9)
    -   [Placeholder](#orgb56f568)
    -   [The world-tag](#org5d8069d)
-   [Staging](#orgba39a4c)
    -   [The law](#org170e07e)
    -   [The guard](#org7e607ca)
    -   [Early or late, per value](#org0b95940)
    -   [What a machine can check](#orgc32fe90)
-   [State: the stem](#org731d000)
    -   [A path is an id](#org15eb842)
-   [Talking to the browser](#org9450f6c)
    -   [Life-cycle without a component model](#org50ab3e1)
    -   [Why we don't bubble](#org4af1653)
    -   [Per-frame work](#orgf7bb368)
    -   [The synchronous window](#org58ec24b)
-   [Three modes](#orgb1865d4)
    -   [SSR](#orga448592)
    -   [CSR](#orgb432d48)
    -   [Offline](#org7f601b0)
-   [Ordering](#org18bf039)
-   [Building with Offworld](#org36d546d)
    -   [No treasure hunt](#orgc406bef)
    -   [Everything is greppable, exactly.](#org169587d)
    -   [The REPL is the whole harness.](#orgd7b4e1b)
-   [What it costs](#org3f5a75b)
-   [Objections](#org5370462)
    -   [Do I smell Greenspun's tenth rule?](#orge34ab17)
    -   [Why not infer the client/server boundary with macros?](#org9e8f232)
    -   [Isn't shipping data slower than shipping code?](#org51de392)
    -   [Why not signals?](#org0041eba)
    -   [Why not cursors?](#org3e3a8da)
    -   [Do I have to register *everything?*](#org802192a)
    -   [Where's the component model?](#org658b79d)
    -   [Is offworld right for my app?](#org616ae7e)
-   [Status](#orga259ac1)
-   [Glossary](#org762cf55)
    -   [Terms from supporting libraries](#org92bf50d)
    -   [Terms from Offworld](#orgfb99255)
    -   [Terms from the literature](#orgf6660ca)
-   [Sources](#orgf1f006c)



<a id="org226acb1"></a>

# Use-case

> "A new life awaits you in the Offworld colonies. The chance to begin
> again in a golden land of opportunity and
> adventure."<sup><a id="fnr.bladerunner_offworld" class="footref" href="#fn.bladerunner_offworld" role="doc-backlink">1</a></sup>

Offworld offers a unified design pattern for hybrid UX/UI.
"Hybrid" means the exact same rendering & <a href="#org845b937">dispatch</a> code can express:

-   <a href="#orgf27679f">SSR</a>: Markup rendered on the server and morphed into the browser DOM
-   <a href="#org6a372c1">CSR</a>: DOM rendered entirely in the browser
-   Offline-capable elements (rendered optimistically on the client,
    stemming from a snapshot the server left behind).

Offworld promotes:

-   Top-down rendering with <a href="#org788aa9e">Replicant</a>-flavored <a href="#orgd0bf6c5">hiccup</a>
-   <a href="#orgd625364">Nexus</a> for data-driven state management
-   User actions tagged by <a href="#org5617283">world</a>: `^::🪐/server`, `^::🪐/client`
-   <a href="#org9d6c43d">Datastar</a> for <a href="#orgf27679f">SSR</a>
-   <a href="#org788aa9e">Replicant</a> for <a href="#org6a372c1">CSR</a>
-   No component model
-   No reactive re-rendering
-   No signals
-   No event bubbling
-   No expression compiler
-   No boundary inference for client/server code


<a id="org5e5cfd8"></a>

# Thesis

Offworld commits to one idea:

**A user-<a href="#org1cb1fa9">intent</a> is a <a href="#orgbae0691">staged computation</a>, split across worlds: 🪐client
and 🌏server. Express it as a plain vector of actions, each tagged
with the <a href="#org5617283">world</a> it runs in.**

The <a href="#org1925853">stage</a> where a given <a href="#org0022b72">action</a> runs then follows from what you've declared:
the <a href="#org5617283">world</a> it's tagged with, and the kind of handler which you've registered:

    ;; The intent behind a click:
    [:button {:on {:click [[::pan-to id 4.89 52.38]
                           [:effects/save path {:center [4.89 52.38]}]]}}
     "Amsterdam"]
    
    ;; Each action, tagged by the world it runs in:
    (nxr/register-effect! ::pan-to      ^::🪐/client (fn [ctx _ id lng lat] ...))
    (nxr/register-effect! :effects/save ^::🪐/server (fn [_ system path v] ...))

One click, one <a href="#org1cb1fa9">intent</a>-vector, two worlds.
When the user clicks, the map is repositioned in the browser, and
the new position is saved on the server.

We <a href="#org1925853">stage</a> the <a href="#org1cb1fa9">intent</a>, because the parts of one interaction can't all
run at the same moment. Only the server knows your app's current
domain state, at render time. Only the client knows which modifier key
you were holding, at the moment you click. Thus, an <a href="#org1cb1fa9">intent</a> is one
value, whose parts are evaluated at different points along that chain,
rather than code that runs all at once.


<a id="org4ac2786"></a>

## Quickstart: run the demo

    bb dev       # shadow-cljs watch + http-kit on :8000 + clerk on :9001
    bb test-clj  # jvm tests
    bb test-cljs # node tests via shadow

-   [localhost:8000](http://localhost:8000) — server-rendered. The browser holds no state; the
    server renders <a href="#orgd0bf6c5">hiccup</a> to HTML and morphs it down an <a href="#org19af95e">SSE</a> connection.
-   [localhost:8000?csr](http://localhost:8000?csr) — the same UI, entirely client-side. <a href="#org788aa9e">Replicant</a>
    reconciles a virtual DOM; no server after the first byte.
-   Cut the server connection, and the <a href="#orgf27679f">SSR</a> page keeps working. See [Offline](#org7f601b0).

The library is eleven files, still under a thousand lines: `offworld.cljc`,
`stem.cljc`, `staging.cljc`, `order.cljc`, `standard.cljc`, `guard.cljc`,
`claim.cljc`, `conn.cljc`, `inline.cljc`, `util.cljc`, `nexus/registry.cljc`.
Everything under `demo/` is the demo.


<a id="org6c3716d"></a>

# Why


<a id="orgf198e17"></a>

## Two paradigms, one program

Server rendering is appealing — one place for state, one language, no
cache to invalidate — but it always needs a server, and the connection
drops. Rich clients are appealing for the opposite reason, resilient
and immediate, but you end up rebuilding in the browser what the
server already had: a store, a query layer, a migration story. Most
codebases pick one and live with it, and switching or combining means
a rewrite. We use Clojure, though. Simplify the fundamental
patterns far enough and the flexibility might come free.


<a id="org9f866fb"></a>

## The stages are real whether or not you name them

The DOM event only exists in the browser, the database only on the
server, and something has to carry a value from one to the
other. Thus, every client/server design has a data pipeline. What
differs is whether the stages are written down anywhere, or
reconstructed in your head each time.

The common approach leaves them implicit and scattered: a fragment of
JavaScript in a string attribute reads `evt.key` (client <a href="#org1925853">stage</a>); an
opaque server-side <a href="#org70920cb">closure</a> holds the business logic (server <a href="#org1925853">stage</a>); a
payload key matched by string convention ties the two together. Three
authoring sites, three languages, nothing that can see all of them at
once.


<a id="org921f2e4"></a>

## Offworld's <a href="#orgf27679f">SSR</a> pipeline

Here's how Offworld models the pipeline. Stages transpire from top to
bottom; computations flow from left to right:

<table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">


<colgroup>
<col  class="org-left" />

<col  class="org-left" />

<col  class="org-left" />

<col  class="org-left" />
</colgroup>
<thead>
<tr>
<th scope="col" class="org-left"><a href="#org1925853">stage</a></th>
<th scope="col" class="org-left">inputs</th>
<th scope="col" class="org-left"><a href="#org0022b72">action</a></th>
<th scope="col" class="org-left">outputs</th>
</tr>
</thead>
<tbody>
<tr>
<td class="org-left"><a href="#org6dc795d">Morph</a></td>
<td class="org-left">↪<a href="#org19af95e">SSE</a> message   &#x2013;&gt;</td>
<td class="org-left"><a href="#org6dc795d">morph</a></td>
<td class="org-left">&#x2013;&gt; new DOM</td>
</tr>
</tbody>
<tbody>
<tr>
<td class="org-left">Client <a href="#org845b937">Dispatch</a></td>
<td class="org-left">actions        &#x2013;&gt;</td>
<td class="org-left">↪interpolate</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">client-event   &#x2013;&gt;</td>
<td class="org-left">expand</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">client-state   &#x2013;&gt;</td>
<td class="org-left">recur↩</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">interpolate</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left"><a href="#org8109de2">effect</a></td>
<td class="org-left">&#x2013;&gt; client-fx</td>
</tr>
</tbody>
<tbody>
<tr>
<td class="org-left">HTTP Request</td>
<td class="org-left">server-actions &#x2013;&gt;</td>
<td class="org-left">interpolate</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">serialize</td>
<td class="org-left">&#x2013;&gt; <a href="#org845b937">dispatch</a> RPC</td>
</tr>
</tbody>
<tbody>
<tr>
<td class="org-left">Server <a href="#org845b937">Dispatch</a></td>
<td class="org-left">server-state   &#x2013;&gt;</td>
<td class="org-left">↪interpolate</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">expand</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">recur↩</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">interpolate</td>
<td class="org-left">&#xa0;</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left"><a href="#org8109de2">effect</a></td>
<td class="org-left">&#x2013;&gt; server-fx</td>
</tr>
</tbody>
<tbody>
<tr>
<td class="org-left">Render</td>
<td class="org-left">server-state   &#x2013;&gt;</td>
<td class="org-left">render</td>
<td class="org-left">&#x2013;&gt; new actions</td>
</tr>

<tr>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#xa0;</td>
<td class="org-left">&#x2013;&gt; <a href="#org19af95e">SSE</a>-message↩</td>
</tr>
</tbody>
</table>

Offworld's bet is that one data structure carrying all the stages is
worth more than the directness you give up.


<a id="org3d11541"></a>

## A function can't cross the wire

Here's what you'd write if nothing were in the way.
It's a button that changes the season:

    [:button {:on {:click #(set-season! :winter)}}
     "Winter"]

That handler is a <a href="#org70920cb">closure</a>. In a browser-only app, this is fine,
because the function is created and called in the same place.

Now, render that same button on the server. The click will happen in a
browser, later, on a different machine. The server hands over text:
HTML, and whatever it carries.

A <a href="#org70920cb">closure</a> has no written form. You can print its source, but the
bindings it captured (a database connection, the current "season", the
user) were never text and can't become text.

So, you do the only thing available: leave the function where it can
run, give it a name, and send the name to the client, along with the
values it uses:

    [:button {:on {:click [[:set-season :winter]]}} "Winter"]

When the user clicks, the client sends that vector back to the server.
The server looks up `:set-season` from a table, finds the function,
applies it to `:winter`. Nothing crossed the wire but a keyword and a
value.

This rewrite follows a standard technique, called <a href="#orgc6af93c">defunctionalization</a>.
It's worth knowing, because once you see it you
see it everywhere. Reynolds (1972)<sup><a id="fnr.reynolds" class="footref" href="#fn.reynolds" role="doc-backlink">2</a></sup> described it as a compiler
technique: replace a function value with a tag plus the values it
captured. Then, run a central <a href="#org845b937">dispatch</a> procedure which
"applies" the tag to the values.

In our case, that's not an optional style choice.  Any framework that runs
one interaction across two machines uses <a href="#orgc6af93c">defunctionalization</a>, because
a <a href="#org70920cb">closure</a> can't be serialized.  The real choice is, what shall we
defunctionalize our <a href="#org70920cb">closure</a> into? For instance,

-   **A **string**:** an event name, a payload key, a JavaScript expression
    pasted into an attribute
-   **A **place**:** a <a href="#orgffa6465">signal</a>, a web-component attr, a named cell in the
    markup whose meaning lives in a callback registered somewhere else
-   **A **macro expression**:** relying on a compiler that infers which
    <a href="#org5617283">world</a> each expression belongs to
-   **A **data-structure**:** for instance, an EDN vector

Offworld chooses data. Not because data is virtuous, but because of what
data admits that the others don't:

-   **It crosses.** A <a href="#org70920cb">closure</a> can't be split across a wire. An <a href="#org0022b72">action</a>,
    though, is a vector of discrete elements. Looking up the first item
    tells you what <a href="#org5617283">world</a> it belongs to, at any <a href="#orge983817">expansion</a> depth.  Without
    this, there's no hybrid UX at all.
-   **It can be substituted into.** <a href="#org5337c23">Interpolation</a> walks the tree and
    replaces what it finds, at every <a href="#org0022b72">action</a> node, in both worlds.
-   **It can be checked before it runs.** Stranded client references and
    unregistered keys can be caught by simply walking the datastrcture,
    without executing anything.
-   **It can be shown.** An item's <a href="#org1925853">stage</a> is clearly laid out on all
    worlds - client, server, or even your editor.
-   **It can be extended by a caller that can't run it.** This is key to
    reusability. A render-fn accepts input arguments as its
    "configuration" contract. Pass `:blue` to configure a button's
    color, or pass an <a href="#org1cb1fa9">intent</a> to configure what happens when it's
    clicked. It can transform the <a href="#org1cb1fa9">intent</a> at render-time, without needing
    to implement or run it.
-   **It can be recorded and re-run.** Offline mode builds a log while the
    connection is down; reconnecting replays it rather than diffing two
    states.
-   **A change to it reads as a change** — an altered vector in a data diff,
    not an altered string of JavaScript.
-   **It can be authorized.** The server can sign an <a href="#org1cb1fa9">intent</a>, or validate its
    items by auth scope.
-   **They can be refunctionalized**. In principle, a prod build could "compile"
    an <a href="#org1cb1fa9">intent</a> back to a chain of handler calls, trading the above benefits back
    in for fast, direct execution.

None of this comes free, and the fair objection is easy to state: a
table of names you look functions up in is an interpreter, and writing
one is usually a mistake.  That's taken up under
[Objections](#org5370462). The short reply is that the interpreter
is there either way. Either it's <a href="#orgd625364">Nexus</a> — written down, inspectable,
the same in both worlds — or it's an unwritten convention in a
colleague's head.


<a id="org45c22bc"></a>

# The loop, walked

One real interaction from the demo, end to end. A button in the
holiday panel, where shift-clicking means something different from
clicking.

    (defn randomize-button [{::🌿/keys [stem]}]
      [:button {:on {:click [[::randomize
                              [:event/key-modifiers]
                              (get-season stem)]]}}
       "Randomize (shift-click to reset)"])

Two of that vector's three elements are at different stages, and the
notation shows which is which.


<a id="org6007457"></a>

## Render — on the server

`(get-season stem)` is an ordinary function call. It runs *now*,
leaving the value `:spring`.

`[:event/key-modifiers]` is not a call. It names a value that doesn't
exist yet because the click hasn't happened.

That's the whole notation. Anything you can compute at render time you
compute with Clojure. Anything you can't, you *name*, and the name
travels.

Then `🪐/replicant->d*` walks the <a href="#orgd0bf6c5">hiccup</a> and turns the `:on` map into
a <a href="#org9d6c43d">Datastar</a> attribute:

    data-on:click="((_sp)=>_sp&&@get('/offworld-dispatch',{payload:{offworld:_sp}}))
                   (nextjournal.offworld.divert('<transit-base64>', evt))"

That string is itself a <a href="#orgc6af93c">defunctionalization</a>, and a dull one on
purpose<sup><a id="fnr.datastar_attr" class="footref" href="#fn.datastar_attr" role="doc-backlink">3</a></sup>.


<a id="org1fb7e3b"></a>

## In the browser

The click fires. `divert` decodes the payload and dispatches it
against the client-marked <a href="#orgd625364">nexus</a> handler-fns. But first, <a href="#orgd625364">nexus</a> interpolates
every <a href="#org0022b72">action</a>, walking it and resolving any known <a href="#orgde0a8bf">placeholder</a>-vectors.
In our example, `[:event/key-modifiers]` resolves against the
real DOM event, yielding the <a href="#org0022b72">action</a> `[[::randomize [:key/shift] :spring]]`.

`::randomize` is client-marked, so it expands here:

    (nxr/register-action! ::randomize ^::🪐/client
      (fn [_ key-mods season]
        (let [path        [::path :to :season]
              reset?      (contains? (into #{} key-mods) :shift)
              rand-season (first (rand-nth (seq (dissoc season->holiday season))))]
          (if reset?
            [[:browser/alert "Holiday season has been reset."]
             [:effects/save path :spring]]
            [[:effects/save path rand-season]]))))

The shift-key branch is decided in the browser with no round trip — an
ordinary `if`, in ordinary Clojure, because by this <a href="#org1925853">stage</a> the value it
tests is a value.

The two effects it returns belong to different worlds.
`:browser/alert` is marked `^::🪐/client`, whereas
`:effects/save` is `^::🪐/server`. <a href="#orgd625364">Nexus</a> executes the client-<a href="#org8109de2">effect</a>,
and sets aside the server-<a href="#org8109de2">effect</a>.

We run a final round of <a href="#org5337c23">interpolation</a> to settle any remaining
client-placeholders, then the server <a href="#org8109de2">effect</a> to the server.


<a id="orged836ee"></a>

## Across the wire, and back

The server-bound remainder is transit-encoded and handed back to the
<a href="#org9d6c43d">Datastar</a> expression, which sends it to `/offworld-dispatch`. There,
<a href="#orgd625364">Nexus</a> drains it — the same loop and the same registry as in the
browser, now reaching the handlers that needed a server, against the
server's own state.

Offworld stops there. How a server <a href="#org8109de2">effect</a> turns into a new DOM is not
the library's business, and several answers are
defensible<sup><a id="fnr.push_policy" class="footref" href="#fn.push_policy" role="doc-backlink">4</a></sup>. The demo takes the simplest — a watch on
the system atom re-renders the whole UI, runs `replicant->d*` over it,
renders it to a string, and pushes it down the <a href="#org19af95e">SSE</a> connection as a
`datastar-patch-elements` event, which <a href="#org9d6c43d">Datastar</a> morphs into the
page. Offworld's half of that contract is narrow: intents in by
request, HTML out.


<a id="orgac6222f"></a>

## The point

One vector, one authoring site, five stages across two runtimes, and
the boundary between those runtimes marked by a single piece of
metadata on a handler — not by a rewrite, not by a second language,
not by a naming convention.

In <a href="#org6a372c1">CSR</a> mode the same code runs with no split: the full handler registry
is used, and every <a href="#org0022b72">action</a> runs on the client. Thus, <a href="#org6a372c1">CSR</a> vs <a href="#orgf27679f">SSR</a> is a
runtime decision, not an authoring one.


<a id="org0b2dc03"></a>

# The vocabulary

Most of this is <a href="#orgd625364">Nexus</a>'s vocabulary, not Offworld's, and <a href="#orgd625364">Nexus</a> defines
it properly<sup><a id="fnr.nexus_nomenclature" class="footref" href="#fn.nexus_nomenclature" role="doc-backlink">5</a></sup>. Below is the short version of
each, plus the part Offworld adds.


<a id="org86e6273"></a>

## <a href="#org1cb1fa9">Intent</a>

A vector of <a href="#org0022b72">action</a>-vectors, under `:on` in <a href="#orgd0bf6c5">hiccup</a>, keyed by DOM event
name.  Life-cycle hooks take the same shape, and
[Datastar
modifiers](https://data-star.dev/reference/attributes#data-on) ride as metadata, so you never hand-roll a debounce. They
are joined onto the attribute name with `__`, so the scroll handler
below renders as `data-on:scroll__throttle.100ms`:

    {:on {:change [[::toggle [:event.target/checked]]]}}
    
    {:replicant/on-mount [[::init id {:center center}]]}
    
    {:on {:scroll ^{:datastar/modifiers [:throttle.100ms]}
          [[::ng/scroll path
            [:event.target/scroll-top]
            [:event.target/scroll-left]]]}}


<a id="org666b544"></a>

## <a href="#orge983817">Expansion</a>

A handler that receives state and arguments and returns *more
actions*. Where decisions live.

    (nxr/register-action! ::ng/scroll ^::🪐/server
      (fn [_ path top left]
        [[:effects/save (concat path [:scroll-top]) top]
         [:effects/save (concat path [:scroll-left]) left]]))

(`register-expansion!` is the same call under <a href="#orgd625364">Nexus</a>'s newer name.)

<a href="#orge983817">Expansion</a> recurs, and each <a href="#org0022b72">action</a> is interpolated just before it
expands. So a handler always receives interpolated values and can
branch on them with plain Clojure — you almost never need to express
control flow as data rather than as code. Where you do, see
[The guard](#org7e607ca).


<a id="org97ae3a9"></a>

## <a href="#org8109de2">Effect</a>

The drain. An <a href="#org8109de2">effect</a> is a leaf: it mutates something and returns
nothing useful.

    {:node/focus            (fn [ctx _ node] (.focus node))
     :event/prevent-default #(.preventDefault (get-evt %))
     :effects/save          ^::🪐/server
                            (fn [_ system path v]
                              (swap! system assoc-in path v))}

A rule that keeps the registry small: *if an argument decides where to
write, that's not a new <a href="#org8109de2">effect</a>.* It's `:effects/save` with a different
path. Registering `:set-scroll-top` beside it is a smell.

A <a href="#org845b937">dispatch</a> that writes three paths does three swaps, and that is
fine. Coalescing belongs at the render, not at the <a href="#org8109de2">effect</a>: a refresh
window that renders at most once per interval<sup><a id="fnr.hyperlith" class="footref" href="#fn.hyperlith" role="doc-backlink">6</a></sup> collapses
any number of writes into one <a href="#org6dc795d">morph</a>, and does that work once for all
connected clients rather than once per <a href="#org845b937">dispatch</a>.


<a id="orgb56f568"></a>

## <a href="#orgde0a8bf">Placeholder</a>

A named value that doesn't exist yet. <a href="#org5337c23">Interpolation</a> replaces the
vector with the value at every <a href="#org0022b72">action</a> node on the way down, so a
<a href="#orgde0a8bf">placeholder</a> that survives one <a href="#orge983817">expansion</a> still resolves before the
<a href="#org8109de2">effect</a> runs.

    {:event.target/value  #(some-> % get-evt .-target .-value)
     :event/key-modifiers (fn [dd]
                            (let [e (:replicant/dom-event dd)]
                              [(when (.-shiftKey e) :shift)
                               (when (.-altKey e) :alt)
                               (when (.-ctrlKey e) :ctrl)]))}

Prefer event-relative placeholders to global
lookups. `:event.target/scroll-top` reads the triggering event's own
target; a <a href="#orgde0a8bf">placeholder</a> doing `(js/document.getElementById
"some-hardcoded-id")` is correct exactly once, and wrong the moment
the component renders twice.


<a id="org5d8069d"></a>

## The <a href="#org5bfa015">world-tag</a>

The only genuinely new idea, and it's one keyword in metadata. Every
handler belongs to one of two worlds — 🌍 the server or 🪐 off-<a href="#org5617283">world</a>,
the browser — and `^::🪐/server` says which. Unmarked means client. In
<a href="#org6a372c1">CSR</a> mode the distinction is ignored entirely.

    (nxr/register-action! ::scan ^::🪐/client (fn [_ plate] ...))
    (nxr/register-action! ::add-filter ^::🪐/server (fn [state path v] ...))
    
    (nextjournal.offworld.nexus.registry/register-many!
     {:nexus/system->state deref
      :nexus/effects       {...}
      :nexus/placeholders  {...}})


<a id="orgba39a4c"></a>

# Staging


<a id="org170e07e"></a>

## The law

The pipeline has five stages — the rows of the table above, read from
one render to the next. Call it the **<a href="#org1925853">stage</a> ladder**:

    render → morph → client → request → server

Two of them hold handlers: `client` and `server`. A handler's <a href="#org5617283">world</a> *is*
its <a href="#org1925853">stage</a>, and there is no finer rung inside a <a href="#org5617283">world</a>, because <a href="#orgd625364">Nexus</a>
drains a <a href="#org845b937">dispatch</a> to completion. An <a href="#orge983817">expansion</a> emitted by an <a href="#orge983817">expansion</a>
still expands; a <a href="#orgde0a8bf">placeholder</a> that survives <a href="#orge983817">expansion</a> still meets the
<a href="#org5337c23">interpolation</a> pass before effects. Being late *within* a <a href="#org5617283">world</a> is not
something that can happen.

The other three are named because time passes in them. `render` is
ordinary Clojure at the authoring site. `morph` and `request` are
transports — nothing authored resolves there, but each costs real
latency, and a message crossing one can be lost, delayed, reordered or
forged. That is what [Ordering](#org18bf039) is about.

**The staging law.** Computation authored at <a href="#org1925853">stage</a> N may **consume** only
values already resolved by some <a href="#org1925853">stage</a> ≤ N. It may **carry** — nest,
restructure, pass through — a reference to a later-<a href="#org1925853">stage</a> value as
opaque data, but never compute with its contents before its <a href="#org1925853">stage</a>
arrives.<sup><a id="fnr.least_power" class="footref" href="#fn.least_power" role="doc-backlink">7</a></sup>

The `randomize-button` above obeys this without saying so: `(get-season
stem)` is consumed at render because the server has it;
`[:event/key-modifiers]` is only carried, because nothing at render time
can know it.

The same obligation turns up sideways, which is worth naming because
it looks at first like a different thing. A reusable render-fn handed
a `:click-ax` by its caller holds a value it may wrap, nest or `into`, and
must not interpret — the carry clause exactly. What differs is where
the opacity comes from. Render *cannot* read `[:event/key-modifiers]`,
because the value does not exist yet. A button *can* read the <a href="#org0022b72">action</a> it
was handed, and declines to, because reading it would couple the
button to a concern that isn't its own. Physics in the first case and
discipline in the second; one rule covers both.

That is also why render is a single rung on the ladder but not a
single moment.  It is a tree of calls with information flowing
strictly downward, so a caller's frame is genuinely earlier than its
callee's, and what the callee receives is already built and not yet
read.

Which gives the rule for when a branch has to be data at all — and it
isn't "whenever you want a conditional", since <a href="#orge983817">expansion</a> recurs and a
handler receives interpolated values it can branch on with an ordinary
`if`:

**A branch has to be data when the value that decides arrives later than
the code that must choose.**

That happens at two distances. Across the wire: the client must decide
whether to go to the server *at all*, using something only the browser
knows. A keydown handler that should commit on `Enter` has no way to ask
the server, because the answer arrives after the round trip it was
trying to avoid — so without a client-side branch every keystroke
becomes a request the server answers by doing nothing. And across a
call: a render-fn must choose between actions its caller injected,
using something that won't exist until the click. A button with
distinct plain-click and alt-click behaviour cannot pick at render
time, and must not look inside either alternative to do it.


<a id="org7e607ca"></a>

## The guard

Offworld ships exactly one construct for that, and deliberately only
one: `guard`, in `nextjournal.offworld.guard`. It is an ordinary
client-side <a href="#orge983817">expansion</a> whose first argument is interpolated like any
other — truthy and it expands to the actions it was handed, falsey and
it expands to nothing.

The first argument is therefore a *value*, not an expression. The
comparison is ordinary Clojure, written inside a <a href="#orgde0a8bf">placeholder</a> that
answers the question:

    (nxr/register-placeholder! ::enter?
      (fn [dd] (= "Enter" (.-key (:replicant/dom-event dd)))))
    
    {:on {:keydown [[::🚦/guard [::enter?]
                     [[::commit [:event.target/value]]]]]}}

When the guard expands to nothing there is no server <a href="#org0022b72">action</a> left for
the <a href="#orga8019e9">divert</a> <a href="#org86ec34f">interceptor</a> to find, so no request goes out. That is the
across-the-wire case, and the elision falls out of the existing
machinery rather than needing a special case anywhere.

The across-a-call case wraps each alternative instead:

    (nxr/register-placeholder! ::alt-held?
      (fn [dd held?] (= held? (.-altKey (:replicant/dom-event dd)))))
    
    (defn button [{:keys [label click-ax alt-click-ax]}]
      [:button {:on {:click [[::🚦/guard [::alt-held? true] alt-click-ax]
                             [::🚦/guard [::alt-held? false] click-ax]]}}
       label])

`button` never reads either <a href="#org0022b72">action</a>, and neither branch is chosen until
the click.  If the alternative that wins turns out to be server-bound
it diverts normally — the guard and the split compose without either
knowing about the other.

Note what neither example contains. There is no `:not`, no `:and`, no
`:cond`, and no notation for comparison at all. A <a href="#orgde0a8bf">placeholder</a> takes
arguments, so the comparison lives inside one and the negation is just
a different argument.  Anything genuinely logical goes in an <a href="#orge983817">expansion</a>
you register, which receives interpolated values and branches in plain
Clojure — the guard is only the zero-registration version of that,
kept too weak to be worth reaching for when a handler would do.

And if a general-purpose guard still reads as the top of a slope — a
conditional today, an expression language by Christmas — then don't
use it. Register an <a href="#org0022b72">action</a> of your own, named for what it means where
you are: `[::commit-on-enter path]` rather than a guard wrapped around a
commit. The branch is then ordinary Clojure inside your own handler,
the call site says more than any combinator would, and there is no
general notation sitting in the vocabulary inviting a second one.

Its second argument is a vector of actions — the same shape `:on` takes
and an <a href="#orge983817">expansion</a> returns — so a caller's injected actions drop
straight in with no wrapping.<sup><a id="fnr.guard_require" class="footref" href="#fn.guard_require" role="doc-backlink">8</a></sup>

Its actions are checked like any others. Once the guard expands, what
it was holding is an ordinary <a href="#org0022b72">action</a>, and the runtime checker sees it
at that point the same way it sees everything else.


<a id="org0b95940"></a>

## Early or late, per value

*Each registered <a href="#orgd625364">nexus</a>-item within an <a href="#org1cb1fa9">intent</a> may run in a different
<a href="#org1925853">stage</a>*.<sup><a id="fnr.partial_eval" class="footref" href="#fn.partial_eval" role="doc-backlink">9</a></sup>

`(get-season stem)` is early — it runs once, at render, and the result
is baked into the markup. The clearest case is a
counter. `[[:effects/save path (inc n)]]` freezes a number at render; an
<a href="#orge983817">expansion</a> of your own — `[[::bump path]]` — names an operation that
recomputes at <a href="#org845b937">dispatch</a> against whatever the state has become.  Ten
clicks faster than the server can re-render land on `1` in the first
spelling and `10` in the second. Same button, same registry; the
difference is entirely what the vector carries.

Neither is right in the abstract. The question at the call site is
whether the argument stands for *the current value of something* or is a
stable identifier — a path, a row id, a mode name — that means the
same thing whenever it is read.  Stable identifiers are always safe
early. It's the "value I read a moment ago" arguments that freeze
whether you meant them to or not. Mostly this doesn't bite, because
the render loop re-takes the snapshot faster than a person can click
again. It bites when something on the render path lags the next click,
when the render loop is deliberately capped below click speed, and
when the <a href="#org845b937">dispatch</a> source isn't a person at all — a drag handler, key
repeat, a script.

The harder case is an early value too large to serialize into an
attribute. Four ways up, most legible first: **inline it**; **carry a
coordinate into immutable history** — Datomic's `basis-t`<sup><a id="fnr.datomic_asof" class="footref" href="#fn.datomic_asof" role="doc-backlink">10</a></sup> — and re-read
`as-of` it at <a href="#org845b937">dispatch</a>, retaining nothing; **stash it server-side and
carry a claim-check token**<sup><a id="fnr.claimcheck" class="footref" href="#fn.claimcheck" role="doc-backlink">11</a></sup>; or **hand over the <a href="#org70920cb">closure</a>**, everything pinned
and nothing legible. Offworld ships three of the four: the
first because it is ordinary Clojure, the third as `claim/stash!`, and
the fourth as the `inline` escape hatch — the last two being the same
per-connection token store holding a value in one case and a <a href="#org70920cb">closure</a> in
the other. Only the second is design rather than code, and it only
exists if your data
layer can name its own past — which is the untested claim underneath
the whole ladder, that the snapshot machinery a UI framework must
invent is inversely proportional to how addressable its data layer is.

One consequence for replay cuts the other way: a late-bound read
resolves against *current* state, so replaying a logged <a href="#org1cb1fa9">intent</a>
re-executes it rather than reconstructing what the render saw. Binding
late reads to a render-time basis would give both fidelity and
durability. Not built.


<a id="orgc32fe90"></a>

## What a machine can check

Two violations are worth naming, and both are the *backward* case, where
a carry has nowhere left to go:

-   `:stranded-client-ref` — a client-<a href="#org5617283">world</a> reference that survived past
    `request` into server-bound actions. It resolves only in the browser,
    that <a href="#org1925853">stage</a> is behind it, and the context its resolver needs is gone.
-   `:unregistered-action` — a dispatched key that resolves to no handler,
    and would otherwise be a silent no-op.

The forward case isn't decidable, and Offworld doesn't guess: whether
a handler *consumes* or *carries* a value lives in its body, so a
later-<a href="#org1925853">stage</a> reference sitting inside an earlier <a href="#org0022b72">action</a> may be a
perfectly legal carry.

**At runtime, as an <a href="#org86ec34f">interceptor</a>.** This is the real check. It sits at
<a href="#orgd625364">Nexus</a>'s `:before-action` <a href="#org86ec34f">interceptor</a> phase<sup><a id="fnr.nexus_interceptors" class="footref" href="#fn.nexus_interceptors" role="doc-backlink">12</a></sup> and
sees every <a href="#org0022b72">action</a> <a href="#orgd625364">Nexus</a> reaches, at any <a href="#orge983817">expansion</a> depth, in
whichever <a href="#org5617283">world</a> it is installed in. An <a href="#org0022b72">action</a> that an <a href="#orge983817">expansion</a>
computed, a caller injected, or a guard nested is by then an ordinary
<a href="#org0022b72">action</a> like any other, and is checked like one.

    (nxr/register-interceptor! (staging/checker))
    
    (staging/warn-on!)   ;; silent until you ask for it
    (staging/report)     ;; grouped summary of everything seen this session

Register it before `client-nexus` appends the <a href="#orga8019e9">divert</a> <a href="#org86ec34f">interceptor</a>, which it
does when you call it, so registering at load time is enough:
interceptors run in order, and <a href="#orga8019e9">divert</a> empties the queue for the
actions it claims. Which <a href="#org5617283">world</a> it runs in decides how much it checks —
a client-<a href="#org1925853">stage</a> reference is legal on the client and stranded only once
it reaches the server, so `:stranded-client-ref` is a server-<a href="#org5617283">world</a> check
while `:unregistered-action` applies in both.

**Over source, as a lint.** The same two checks are pure functions —
`lookup`, `tag`, `refs`, `stranded-at-server`, `unregistered-actions` — so you
can also run them over a <a href="#org845b937">dispatch</a> in a test, in a REPL, or over source
that was never loaded.  That is worth having: it puts violations in
front of you before anything runs.

It is a lint, though, and not a verdict, and the gap is structural
rather than a matter of effort. A static pass sees only what the
source literally spells, and most dispatches aren't literal — actions
get computed by expansions, handed in by callers, assembled out of
state. So a clean static pass means *no violations among the ones that
could be read*, and never *no violations*. Taken that way it is
useful. Taken as validation it will eventually lie to you, which is
why the <a href="#org86ec34f">interceptor</a> is the one that runs in anger.

Either vantage works at all because a <a href="#org845b937">dispatch</a> is a tree of
keyword-headed vectors, and every key is a registry entry carrying the
<a href="#org5617283">world</a> tag — so the whole analysis is a `tree-seq`, a `get-in` and a `meta`,
never running a handler and never reading one. Hand it a <a href="#org70920cb">closure</a>
instead and there is nothing to walk, since the only way to learn what
a body needs is to execute it.


<a id="org731d000"></a>

# State: the <a href="#org0377cd1">stem</a>

> What comes from something else? <a href="#org0377cd1">Stem</a>. <sup><a id="fnr.bladerunner_stem" class="footref" href="#fn.bladerunner_stem" role="doc-backlink">13</a></sup>

Two familiar options, both real. **<a href="#org48ba48b">Prop drilling</a>** gives honest signatures
— the arguments *are* the scope — but changing the UI tree means
re-drilling everything, and every render-fn invents its own convention
for where state lives. **Global state with a query registry** is concise,
and most of the Clojure community has settled there, but now a
render-fn's real dependencies aren't in its signature: what breaks if
I delete this, what is `::macguffin`, why does removing this component
break it? re-frame answers with tooling<sup><a id="fnr.reframe10x" class="footref" href="#fn.reframe10x" role="doc-backlink">14</a></sup>.

With top-down rendering you can have both, because the global state
can simply *be* one of the arguments. Immutable data makes this free —
you're passing a pointer, not copying a <a href="#org5617283">world</a>.

That's the **<a href="#org0377cd1">stem</a>**: the whole state carried down as a value, alongside a
**path** saying where *this* render-fn lives inside it.

    (defn main-view [state]
      (for [k [:dresden :hanover]]
        (station-panel (🌿/+ state [:stations k] {:station k}))))

`🌿/+` extends the path and merges in configuration; `🌿/>` replaces the
path; `🌿/local` reads this component's own subtree back out. The <a href="#org0377cd1">stem</a>
rides along untouched. Three concerns stay apart by convention:

-   **Configuration** — plain keys. The caller owns the value, its locality,
    its change.
-   **Local state** — reached through `🌿/local`, written at `🌿/path`. *This*
    function owns it.
-   **Domain** — read out of the <a href="#org0377cd1">stem</a> with an ordinary getter. UI should
    model neither its locality nor its change; that's what puts you in
    the tar pit<sup><a id="fnr.tarpit" class="footref" href="#fn.tarpit" role="doc-backlink">15</a></sup>.

    (defn get-season [stem] (get-in stem [::path :to :season] :spring))
    
    (defn get-day {::🌿/deps #{`get-season}} [stem]
      (season->holiday (get-season stem)))

Getters are just functions of the <a href="#org0377cd1">stem</a>. `::🌿/deps` is a seed for
dependency tracing<sup><a id="fnr.stem_deps" class="footref" href="#fn.stem_deps" role="doc-backlink">16</a></sup>; treat it as documentation today.


<a id="org15eb842"></a>

## A path is an id

`🌿/id` is a pure derivation of `🌿/path`, so an element's name comes from
its position in the *render* composition — not from a mount-time
counter, an insertion order, or a place in the DOM. That's what makes
it survive morphing: <a href="#org9d6c43d">Datastar</a> replaces nodes underneath you, but it
cannot perturb a name the markup itself derives: the name is a
function of what the thing *is* in the composition, not of when or where
it appeared.

The `::🌿/el` <a href="#orgde0a8bf">placeholder</a> closes the loop, resolving a path or id to a
live node at client-<a href="#org845b937">dispatch</a> time:

    [[:node/hide-popover [::🌿/el popover-id]]
     [:node/focus [::🌿/el choice-id]]]

To build a persistent "component" with JavaScript batteries included,
all you need is a path and an id. That sounds like two things. It's
one.


<a id="org9450f6c"></a>

# Talking to the browser


<a id="org50ab3e1"></a>

## Life-cycle without a component model

<a href="#org788aa9e">Replicant</a> hands the DOM node to <a href="#orgde0a8bf">placeholder</a>- and <a href="#org8109de2">effect</a>-handlers, and
that's enough. No refs, no hooks, no `componentDidMount`.

The demo's map widget is the whole pattern in twenty lines. On mount,
a client <a href="#org8109de2">effect</a> constructs a MapLibre object and calls the injected
`remember` fn, which stores it in a `WeakMap` keyed by the node. Later,
another element's handler names the node's id and `🪐/recall` gets the
object back.

    [:div {:id                 id
           :data-ignore-morph  true
           :replicant/on-mount [[::init id {:center center}]]}]
    
    [:button {:on {:click [[::pan-to id 4.89 52.38]
                           [:effects/save path {:center [4.89 52.38]}]]}}
     "Amsterdam"]

The `WeakMap` subordinates the JS object's lifetime to the
node's. Unmount the div and the map is garbage; mount it again and
`on-mount` builds a fresh one, seeded from the state that same click
already saved. Persistence across mounts — and across sessions, in <a href="#orgf27679f">SSR</a>
— falls out of saving the state and reinitializing from it. Because
the pattern is portable data it is identical in all three modes:
<a href="#org9d6c43d">Datastar</a>'s <a href="#org6dc795d">morph</a> does the mounting in <a href="#orgf27679f">SSR</a>, <a href="#org788aa9e">Replicant</a>'s reconciler in
<a href="#org6a372c1">CSR</a>, and neither the render-fn nor the handler can tell.


<a id="org4af1653"></a>

## Why we don't bubble

The DOM event system fuses three unrelated jobs<sup><a id="fnr.bubbling" class="footref" href="#fn.bubbling" role="doc-backlink">17</a></sup>:

-   **Delegation**: one listener serving many elements. A simple transport
    mechanism.
-   **Default-<a href="#org0022b72">action</a> negotiation**: for instance, form submit, navigation
    checkbox toggle.  The one channel the browser offers for its own
    participation.
-   **Announcement**: an inner element throws a `CustomEvent`, and
    ancestors may subscribe and react to it.  Offworld considers this a
    redundant, legacy architecture, reserving it as an escape hatch.


### The usual reason to delegate doesn't apply.

You delegate to a stable ancestor because the server swaps in new HTML
and your imperatively-attached listeners are gone. We never attach
imperatively: the listener is an attribute in the rendered markup, so
its call site ships with the DOM on every <a href="#org6dc795d">morph</a>.


### The cost of announcement is an obligation, not a name.

Salesforce's guidance is blunt: "When an event bubbles, it becomes
part of your component's API and every consumer along the event's path
must understand the event." With Clojure we don't worry about
collisions, since we have namespaced keys. But, the shared obligation
to "understand the event" remains. And `stopPropagation` makes it
worse by giving <a href="#org5623687">ambient authority</a> to a leaf element: it silences
handlers it has never heard of, whether that's analytics, focus
management or a parent's key-press router. DOM events (as
architecture) don't provide much that we don't already
have. `dispatchEvent` runs its listeners on the current call stack, so
what it buys over a direct call is narrower than it
looks<sup><a id="fnr.dispatch_event" class="footref" href="#fn.dispatch_event" role="doc-backlink">18</a></sup>.


### Bubbling routes by containment; injection routes by naming.

Containment routing makes the topology of your markup part of your
control-flow graph. Wrap something in a div, move a cell into a sticky
header, and you have silently changed who hears an <a href="#org1cb1fa9">intent</a>. In the case of React,
once placement diverged from logical composition, they built a second propagation
path that follows composition, instead of containment<sup><a id="fnr.createportal" class="footref" href="#fn.createportal" role="doc-backlink">19</a></sup>.

**So what do we do instead?** We pass the ids in, and the <a href="#org1cb1fa9">intent</a> names its
recipients. Here is the omnibox handling `Enter` on a choice item — one
handler acting on three named elements plus the event itself, all
spelled at the site that decides:

    "Enter" [[:event/prevent-default]
             [:node/hide-popover [::🌿/el popover-id]]
             [:node/blur [::🌿/el anchor-id]]
             [:node/set-checked [::🌿/el choice-id] true]
             [:effects/conj (conj path :filters) (first filters-to-add) #{}]]

This is exactly what a component framework would express by
bubbling. Here the render code says what the keypress *means*, nothing
along any path is obliged to know, and `[:event/prevent-default]` sits
in the same vector — default-<a href="#org0022b72">action</a> negotiation is reachable from
data, so we never needed the event as an architecture, only as a
transport. The failure modes differ too: an injected id can be missing
and `::🌿/el` resolves to nothing. That's something we can \`assert\`.  A
bubbled event with no listeners is indistinguishable from one that
worked.

**When does bubbling still win?** Not on subscriber count — any consumer
set derivable at render time is injectable, and under `view = f(state)`
that's all of them. The real discriminator is authorship. **Bubbling is
the interop protocol with code that isn't in your render tree; inside
your render tree, injection does the same job, only safer and more
explicit.**

Which leaves DOM you didn't render, consumers arriving without a
re-render, and the browser as the consumer of a default <a href="#org0022b72">action</a>.

Also, one concession: A single gesture can reasonably fire two
differently-named events: a per-frame one that never leaves the
client, and one on release that commits. For the per-frame leg a
public DOM name is right, because the widget is *offering* something any
container may subscribe to rather than addressing a recipient it could
have named.  An ancestor can observe, cancel or re-emit it, though
never restructure what was *meant* — which is what a middle layer does
to an <a href="#org1cb1fa9">intent</a> that arrives as data.

Likewise, delegation stays available as an optimization: one ancestor
listener reading `.target`, instead of N handlers attached to N
elements, can save lots of markup, if you don't mind giving up some
explicitness. Prefer per-element listeners, and delegate when you have
measured a reason to.


<a id="orgf7bb368"></a>

## Per-frame work

Gestures sample. A drag fires `pointermove` sixty times a second, and every one of
them is a <a href="#org845b937">dispatch</a>.

**Sample client actions freely. Commit to the server once.**

In <a href="#orgf27679f">SSR</a> every <a href="#org845b937">dispatch</a> runs `divert`, and only a <a href="#org845b937">dispatch</a> still holding server
actions becomes a request: the generated attribute is `_sp && @get(…)`, so one
that resolves entirely in the browser never calls out. For instance:

    {:on {:pointerdown [[::rz/grab]]
          :pointermove [[::🚦/guard [::rz/dragging?]
                         [[::rz/preview grid-id idx]]]]
          :pointerup   [[::🚦/guard [::rz/dragging?]
                         [[::rz/commit path c [::rz/width grid-id idx]]]]]}}

`::rz/preview` is client-marked and writes `gridTemplateColumns` directly, so the
per-frame leg stays in the browser. `::rz/commit` is server-marked and sits behind
a guard that opens only on `pointerup`. Sixty dispatches, one request.

Note which value crosses. The width is never accumulated during the drag — it is
read back off the element at commit time by `[::rz/width grid-id idx]`, at the
last moment before the <a href="#org0022b72">action</a> leaves. The preview writes to the DOM and the
commit reads from it, so no drag state has to live anywhere.

Per-frame <a href="#org845b937">dispatch</a> is not free: a transit decode and a <a href="#orgd625364">Nexus</a> pass each time. It is
cheap enough to be the default, and what it buys is a gesture whose behaviour is
spelled at the element like everything else. The alternative — one client <a href="#org8109de2">effect</a>
owning a `requestAnimationFrame` loop, started and torn down by two intents — is
faster and illegible by comparison, an escape hatch for a seam you have measured
and found wanting.

One thing this doesn't settle. Between the commit and the <a href="#org6dc795d">morph</a> that confirms it,
something has to *own* the property the gesture was changing and keep re-asserting
it across any <a href="#org6dc795d">morph</a> landing in the gap. The resizer gets away with it because the
drag writes the same property the server's render will write. Offworld has places
to hold a client-owned value (`data-ignore-morph`, <a href="#org788aa9e">Replicant</a>'s per-node memory)
and no vocabulary at all for the *handoff* — when the client may stop asserting.
That is hand-rolled per gesture today, and it is the one part of a rich seam this
design doesn't make legible.


<a id="org58ec24b"></a>

## The synchronous window

The browser honours `preventDefault` only while it is dispatching the event:
inside the handler it called, in the same task. That window is not one of
Offworld's stages — it is the browser mechanism that *starts* them — and it has
closed by the time anything asynchronous resumes. So `[:event/prevent-default]`
has to be a client <a href="#org8109de2">effect</a>, and it has to be reached before the <a href="#org845b937">dispatch</a> yields.

<a href="#orgd625364">Nexus</a> makes that straightforward, because it interleaves <a href="#org5337c23">interpolation</a> and effects
rather than resolving an <a href="#org1cb1fa9">intent</a> up front. Each <a href="#org0022b72">action</a> is interpolated as it is
reached, and its <a href="#org8109de2">effect</a> runs before the next <a href="#org0022b72">action</a> is looked at, so
`[:event/prevent-default]` written first runs first — before the placeholders in
later actions have been read at all. The same property cuts the other way and is
worth knowing: a <a href="#orgde0a8bf">placeholder</a> in a later <a href="#org0022b72">action</a> reads the *live* event at the
moment it is reached, not a snapshot taken when the <a href="#org845b937">dispatch</a> began.


<a id="orgb1865d4"></a>

# Three modes

The same expression, three runtimes. This is what the library is named
after.


<a id="orga448592"></a>

## <a href="#orgf27679f">SSR</a>

The browser holds no state. This is the mode walked through above:
`divert` splits, and the server drains its half. What comes next is the
part the walkthrough left open, and the demo's answer is to re-render
on every change:

    (add-watch system ::ui/render
               (fn [_ _ _ new-state]
                 (broadcast-elements!
                  (sse-message {:event "datastar-patch-elements"
                                :lines [["elements" (-> new-state
                                                        🌿/init-state
                                                        ui/render
                                                        🪐/replicant->d*
                                                        rstr/render)]]}))))

Yes — that re-renders everything, every time, and sends it over the
wire. Brotli and <a href="#org6dc795d">morph</a> diffing make it cheaper than it sounds, and it
buys one state in one place with the DOM a pure function of
it. Committing to top-down rendering is the trade.

That schedule is the demo's choice and the first thing to revisit
under load. A burst of eight dispatches costs eight pushes under this
watch and one under a loop that pushes on a fixed interval, without a
line of view code changing — scheduling changes how often the browser
is told, never what is true.


<a id="orgb432d48"></a>

## <a href="#org6a372c1">CSR</a>

<a href="#org788aa9e">Replicant</a>'s <a href="#org845b937">dispatch</a> goes straight to <a href="#orgd625364">Nexus</a> against a local atom. No
client view is applied and no diverting <a href="#org86ec34f">interceptor</a> is installed, so
nothing splits and no payload is ever encoded.

    (r/set-dispatch!
     (fn [dispatch-data actions]
       (if js/navigator.onLine
         (nxr/dispatch system dispatch-data actions)
         (🌠/offline-dispatch dispatch-data actions))))

The intended arc is that the <a href="#orgf27679f">SSR</a> page paints from server HTML with no
JavaScript on the critical path, prefetches this bundle, and hands off
in the background so the app becomes a rich client afterwards. The
prefetch and the handoff hook are wired (release builds only). The
last step isn't: the handed-off bundle registers its <a href="#org845b937">dispatch</a> fn but
never takes over rendering, so today `?csr` is how you actually get
the client-rendered app.


<a id="org7f601b0"></a>

## Offline

This is where representing intents as data stops being an aesthetic
preference.

An <a href="#orgf27679f">SSR</a> page that loses its connection is normally dead — its state
lives on a server it can't reach. Offworld's answer is to have the
server *leave a snapshot behind*. `offline-capable` wraps a subtree in
a div, encodes the paths that subtree actually needs, and parks them
in a `data-offworld-sync` attribute:

    (🌠/offline-capable
     {:id           "scan-game-offline"
      :render-fn    #'scan/offline-game
      :select-paths #{[::scan/scans] [::scan/plates]}
      ::🌿/path     [:scan-game]
      ::🌿/stem     stem}
     (scan/game (🌿/+ state [:scan-game])))

On the `offline` event, offworld collects every such node,
rebuilds a <a href="#org0377cd1">stem</a> from the parked paths, flips the ux to `:csr`, and
re-renders those subtrees with <a href="#org788aa9e">Replicant</a> (in the browser, from the
same render-fns, with no server). Dispatches keep working: server-bound
actions can't run, so `offline-dispatch` appends them to an <a href="#org0022b72">action</a> log
and runs everything else locally.

Once the server goes back online, offworld sends over the <a href="#org0022b72">action</a> log.
Then, it's up to the server to reconcile those prodigal actions with
its latest state. Naively, just dispatching them could work fine.

Reconciling the offline <a href="#org0022b72">action</a>-log should result in a new <a href="#org788aa9e">replicant</a>
system-state, causing a <a href="#org6dc795d">morph</a> back to `:ssr` mode.


<a id="org18bf039"></a>

# Ordering

Dispatches race. A throttled scroll handler can put three actions on
the wire and have them arrive out of order, and `assoc-in` is not
commutative when the same path is written twice.

`nextjournal.offworld.order` holds policies as metadata on the
<a href="#org845b937">dispatch</a>.  `:seq-gate` stamps a sequence number per key and the server
drops anything that isn't next — correct for "latest wins" streams
like scroll position.  `:bounded-buffer` holds out-of-order actions in
a small buffer and flushes on contiguity, overflow, or timeout —
correct when you need every <a href="#org0022b72">action</a>, in order.

Ordering is a policy, not part of the client/server boundary, so it's
two interceptors of its own rather than a step buried in the
split. Nothing in this library installs either:

    (nxr/register-interceptor! (📈/proposing !counters))    ; client
    (nxr/register-interceptor! (📈/checking !actor-state))  ; server

`proposing` runs `:after-dispatch` and stamps whatever is on its way
to the server. `checking` runs `:before-dispatch` and applies the
policy before anything runs. A <a href="#org845b937">dispatch</a> carrying no policy passes
through both untouched, and a <a href="#org845b937">dispatch</a> that skips ahead when nobody is
checking simply runs — the honest shape of an opt-in guarantee.

Nothing runs unless a policy asks for it: a `:drop` says so outright,
and a bare `:timeout` means *held, not yet*, so a buffered gap waits
rather than executing while it waits for its predecessor. Scheduling
stays yours — `checking` takes an `:on-timeout` callback and hands the
request over rather than reaching for a timer the library has no
business owning; call `handle-timeout` when it fires.

Both policies and both interceptors are implemented and tested. What
has no worked example yet is a UI that actually needs one, so treat
the policy set as two plausible answers rather than two proven ones.


<a id="org36d546d"></a>

# Building with Offworld


<a id="orgc406bef"></a>

## No treasure hunt

To find out what a click does in an event-driven
UI you go looking: the listener might be on the element or delegated
three files away, something on the path might call `stopPropagation`,
a shadow boundary might mean the `target` you'd inspect isn't the one
that fired. The wiring is only complete at runtime, in the live DOM —
which makes the DOM the document. Here the answer is the form you're
already looking at: `randomize-button` states its whole story where
it's written, no ancestor will intercept it, nothing elsewhere has
quietly subscribed. That runs the other way too — delete a render-fn
and nothing silently loses a listener; move a subtree into a wrapper
div and no behaviour changes.


<a id="org169587d"></a>

## Everything is greppable, exactly.

Every name is a namespaced Clojure keyword, with one definition site
and a closed set of use sites. `rg '::randomize'` finds the registration
and every <a href="#org845b937">dispatch</a>, no false positives. Compare a `CustomEvent` called
`"columnresize"`: a string in two unrelated files, no definition site,
nothing distinguishing it from the same word in a comment. Miss a site
while renaming and it surfaces as an `:unregistered-action` naming the
key, not as a listener that quietly stops firing.


<a id="orgd7b4e1b"></a>

## The REPL is the whole harness.

An <a href="#org1cb1fa9">intent</a> is a value your render expression *returns*, so you can look
at it — an inline `def`, or hashp's `#p`<sup><a id="fnr.hashp" class="footref" href="#fn.hashp" role="doc-backlink">20</a></sup>, anywhere in the composition
chain:

    [:button {:on {:click #p [[::randomize [:event/key-modifiers]
                               (get-season stem)]]}}
     "Randomize (shift-click to reset)"]
    
    ;; => [[:nextjournal.offworld.demo.ui.holiday/randomize
    ;;      [:event/key-modifiers] :spring]]

What prints is the finished <a href="#orgbae0691">staged computation</a> *at that call site*:
render-<a href="#org1925853">stage</a> values resolved, later-<a href="#org1925853">stage</a> names still standing as
data. Everything the outer callers injected is in there too, because
injection is render-time function application: what prints is
everything the callers contributed, not only the part written here. Capture two across a state
change and `clojure.data/diff` them.

There is no equivalent when behaviour is *announced* rather than
composed: a bubbling event has no value to look at, and nothing exists
until a real gesture at runtime.

The rest follows: a <a href="#org845b937">dispatch</a> is a value, so you can build one and run
the machinery over it with no browser and no server. Render-fns
likewise — the arguments *are* the scope, so calling one is calling a
function. No mounting, no test renderer, no jsdom.

    (nexus/expand-actions (nxr/get-registry) nil actions dispatch-data)
    (staging/unregistered-actions (nxr/get-registry) dispatch)
    (station-panel (🌿/+ state [:stations :dresden] {:station :dresden}))


### What this gives an agent

Locality, greppability and a REPL-runnable <a href="#org845b937">dispatch</a> matter more for a
coding agent than for a person, because an agent has less context and
no eyes. The unit of change is one form in one file, and editing an
<a href="#org1cb1fa9">intent</a> needs no model of the runtime DOM because the DOM never held
the wiring.  Verification needs no browser: build a <a href="#org845b937">dispatch</a>, expand
it, assert on the result, or run `staging/report` — where most UI work
is otherwise unverifiable except by screenshot. Mistakes are named
rather than reproduced. And the vocabulary is a map you can print:
`(nxr/get-registry)` is the whole contract.


<a id="org3f5a75b"></a>

# What it costs

**A call site doesn't show its staging.** Reading `[[:effects/save path
v] [::pan-to id 4.89 52.38]]`, nothing tells you the first runs on the
server and the second in the browser. It's legible as data and
illegible as *staging*. Nothing is actually hidden — the registry
declares <a href="#org5617283">world</a> and shape for every key — so this is a tooling gap, and
largely a closed one: the **<a href="#orga8019e9">divert</a> atlas** statically scans every
registration site and classifies each key by <a href="#org5617283">world</a> and kind, and
`offworld-atlas-mode` font-locks that into any Clojure buffer (earth
green for server, off-<a href="#org5617283">world</a> violet for client, texture for <a href="#org8109de2">effect</a> /
<a href="#orge983817">expansion</a> / <a href="#orgde0a8bf">placeholder</a>), with the reading on hover and at point via
eldoc. The same classifier builds a synthetic registry and runs the
staging checks without loading or executing anything. Caveats, none
fatal: the classification is *per key*, so it says what
`:effects/save` always is rather than whether this <a href="#org845b937">dispatch</a> is
well-formed; the analysis is textual rather than macroexpanded; and
the faces table is a generated snapshot that drifts unless
regenerated. It is a lint, in other words, with the coverage a lint
has — the runtime checker is what actually validates a <a href="#org845b937">dispatch</a>.

**Placeholders are an ambient rule, and an unresolved one is
invisible.** The sharpest edge in the design. <a href="#org5337c23">Interpolation</a> walks the
entire <a href="#org0022b72">action</a> tree, children first, recognising a <a href="#orgde0a8bf">placeholder</a> by
looking a vector's head up in the registry — at any depth, in any
argument position, map keys included. So a <a href="#orgde0a8bf">placeholder</a> that fails to
resolve, through a typo or a stale client bundle or a registration
that never ran, is indistinguishable from a vector you meant to
write. It travels on as data and nothing reports it. The tell is
specific: a value arrives server-side as
`["~:event.target/scroll-top"]` instead of a number while the rendered
HTML looks perfectly correct. The reverse direction is live too —
<a href="#org5337c23">interpolation</a> runs again on the server against the *server's*
registry, so a keyword-headed literal you authored can be captured by
a <a href="#orgde0a8bf">placeholder</a> your code never heard of.

The checker can't help, and `staging.cljc` admits why:
`keyword-headed?` is "the shape of both a dispatched <a href="#org0022b72">action</a> and a
reference". Head position is discriminated by structure and is
checkable; argument position is discriminated by registry membership,
which is global, load-order dependent, and may differ between the two
runtimes. The honest fix isn't a lint but a distinguishable *value* —
a tagged literal or a record — so "is this a <a href="#orgde0a8bf">placeholder</a>?" is a
property of the value rather than of whatever happens to be in a
registry. Unresolved would become an error, and authored data could
never be captured. Not done.

**A silent no-op is possible.** An unregistered <a href="#org0022b72">action</a> does nothing and
says nothing on its own. Install `staging/checker` and turn `warn-on!`
on in dev.

**Stack traces point at the loop.** An <a href="#org8109de2">effect</a> that throws traces through
<a href="#orgd625364">Nexus</a>'s <a href="#org845b937">dispatch</a> loop, not the render-fn that composed the
<a href="#org1cb1fa9">intent</a>. <a href="#orgd625364">Nexus</a>'s <a href="#org0022b72">action</a> log (`nexus.action-log/create-log`, with a
Dataspex panel) recovers most of what the trace doesn't tell you, but
the trace is still one indirection removed.

**Explicit state is still explicit.** The <a href="#org0377cd1">stem</a> makes threading cheap,
but a render-fn needing three things still takes three things. That's
the trade for signatures that tell the truth, and it is a trade.

**And underneath all of them: this is an execution model.** Any library
that decides how an interaction is sequenced carries a risk its
authors rarely spell out — a lesson re-frame learned about its own
machinery over a decade, and one Offworld inherits by being downstream
of it. The mitigation is deliberate: keep the machinery small enough
to read end to end (three kinds of registration, one loop, one
metadata tag), and make what it decides inspectable rather than
ambient.  <a href="#orgde0a8bf">Placeholder</a> <a href="#org5337c23">interpolation</a> is the one rule that currently
fails that second test.


<a id="org5370462"></a>

# Objections


<a id="orge34ab17"></a>

## Do I smell <a href="#orgab3ec45">Greenspun's tenth rule</a>?

Perhaps. But, a careful study
of server-side rendering shows that some kind of reified procedure
(i.e. <a href="#orgc6af93c">defunctionalization</a>) is unavoidable. The question isn't whether
to build an abstract machine, but whether to admit you have built one.

The worst part of lisp-in-lisp is when data represents control-flow.
Offworld offers a standard 


<a id="org9e8f232"></a>

## Why not infer the client/server boundary with macros?

Hyper and Electric Clojure do versions of this, offering real
ergonomics.  What they don't leave you with is a value. There's
nothing to walk, check, tag or log. Changes to intents show up as
new compiler output.


<a id="org51de392"></a>

## Isn't shipping data slower than shipping code?

We kept confusing two costs, and measuring a large table separated
them. On the *wire*, compression absorbs the cost: inline intents are
enormously repetitive, which is the case compression handles best.
Rendering each <a href="#org1cb1fa9">intent</a> with a shared template or an opaque server-held
token only buys a marginal further reduction.

On *parse*, those replacements don't help at all, because the browser
decompresses back to full text and parse time tracks the uncompressed
byte count, which they barely move. The opaque-reference alternative
is the smaller one, not the faster one.

Intents as data are a pattern, not an obligation. For instance, a
statically-known <a href="#org845b937">dispatch</a> could in principle compile to direct client
code, at the cost of two implementations that must be proven to agree.


<a id="org0041eba"></a>

## Why not signals?

A <a href="#orgffa6465">signal</a> is a defunctionalized *setter on a mutable place* —
stringly-typed, its meaning in a callback rather than on the
wire. Fine for <a href="#org9b814ba">residual state</a> that wants a cell (a column width,
`<details open>`); poor for <a href="#org1cb1fa9">intent</a>, which wants to be logged, replayed
and carried across a boundary. Offworld uses neither: residual
presentation state goes on the DOM node or in <a href="#org788aa9e">Replicant</a>'s per-node
memory. Read the critique narrowly, though — "not data" is not the
same as "a place", and a client-side capability addressed by an id is
neither a mutable cell nor a setter. What the critique lands on is
making the place-cell your general model of client state.


<a id="org3e3a8da"></a>

## Why not cursors?

A <a href="#org30a964a">cursor</a> fuses read and write onto one path, and Offworld already
hands a render-fn both halves separately — `🌿/local` reads at the
path, `[:effects/save path v]` writes at it. Why not fuse them?

Because that defunctionalizes the *place* rather than the *<a href="#org1cb1fa9">intent</a>*,
and the two aren't recoverable from each other. From `[:effects/save
[:panel :season] :winter]` you can't tell someone shift-clicked
Randomize; from `[::randomize [:shift] :spring]` you can recover both
what happened and the write it produces (by running the <a href="#orge983817">expansion</a>).

Which decides three things once a write has to leave the process: you
can ask whether this user may randomize the season, but not
meaningfully whether they may set a path to a value; replaying places
overwrites where replaying intents re-decides; and a middle layer can
restructure an <a href="#org1cb1fa9">intent</a> it doesn't understand, where a write it can only
intercept. There's a mechanical reason too — a <a href="#org30a964a">cursor</a> is a <a href="#org70920cb">closure</a>
over a path, so it can't cross the wire, and defunctionalizing it
gives you back `[:effects/save path v]`. A <a href="#org30a964a">cursor</a> is what you have
*before* the boundary forces your hand.

We keep the addressing half: `🌿/+` extends a path, `🌿/local` reads
at it — the read end of a <a href="#org30a964a">cursor</a> with no write end attached. Reading
at a path was never the problem — which makes the <a href="#org0377cd1">stem</a> itself fair to
point at, since `🌿/local` plus `[:effects/save path v]` *is* a <a href="#org30a964a">cursor</a>
with the halves left apart.

Offworld reaches for this <a href="#org30a964a">cursor</a>-like pattern deliberately for a
component's own local state. This is sensible, beacuse the tree of
local-states is isomorphic with the render call tree. Addressing by
path doesn't couple anything that isn't already coupled.

The line is about the place model *globally*, for shared and domain
state, and then discovering every component knows the shape of the
whole store. re-frame put it more bluntly a decade ago<sup><a id="fnr.reframe_cursors" class="footref" href="#fn.reframe_cursors" role="doc-backlink">21</a></sup>: "Please, just
say no. We already know where that goes. As your programs get bigger,
the use of these two-way constructs will encourage control logic into
all the wrong places and you'll end up with a tire-fire of an
Architecture."

The usual framing of that argument — command-query separation — is the
weaker version: a strictly one-way system dispatching `[:set-path [:a
:b] 1]` everywhere has obeyed it and gained nothing. The problem isn't
that reads and writes travel together; it's that the write names a
location instead of a meaning.

And the concession: for genuinely <a href="#org9b814ba">residual state</a> — is this disclosure
open, what is half-typed in this box — the <a href="#org1cb1fa9">intent</a> *is* the
place-write, and "you made me register an <a href="#org0022b72">action</a> to toggle a boolean"
is fair. The reason to hold the line is that whether a piece of state
is residual turns out to be knowable per application, not per widget.


<a id="org802192a"></a>

## Do I have to register *everything?*

For a one-off server operation, no. The `inline` macro registers a
<a href="#org70920cb">closure</a> at render time against the current connection and emits
`[[::inline/invoke "<token>"]]`; the server looks the token up and
calls it, and the table is dropped when the connection closes.

It costs every property listed under [A function can't cross the wire](#org3d11541):
the <a href="#org1cb1fa9">intent</a> on the wire is a UUID, so there's nothing to check, colour,
authorize by shape, or replay — the token dies with the connection. It
mints a fresh token per render, so an attribute a stable key would
leave alone churns on each <a href="#org6dc795d">morph</a>. And it runs only on the server, so
it's no answer to the residual-*client*-state complaint above. Two
escape hatches, two different complaints, neither covering the other.


<a id="org658b79d"></a>

## Where's the component model?

There isn't one, and the demo does without: a path for identity, an id
for addressing, a `WeakMap` for lifetime, life-cycle hooks dispatching
ordinary actions. Adding one would mostly mean rebuilding what the DOM
already does.


<a id="org616ae7e"></a>

## Is offworld right for my app?

Maybe not. If your app is connected-only and CRUD-shaped, the simplest
thing that could work is one global default — every interaction an
opaque server <a href="#org70920cb">closure</a> behind a minted key, no registry, no <a href="#org5617283">world</a> tag,
no staging vocabulary. Designs of exactly that shape run real
production apps today (see instabooks.io).

Even in an app that never renders a frame in the browser, one data DSL
spanning client and server state at a single authoring site is an
ergonomic win, over stringly-typed attribute cells and ad-hoc JavaScript.


<a id="orga259ac1"></a>

# Status

This is a research library, not a product.

-   **Solid:** the <a href="#org845b937">dispatch</a> split, placeholders, the <a href="#org5617283">world</a> tag, <a href="#orgf27679f">SSR</a> and <a href="#org6a372c1">CSR</a>
    modes, the <a href="#org0377cd1">stem</a>, the staging analysis and its runtime checker, the
    guard, `inline`.
-   **Implemented and tested, no worked example:** both ordering policies
    and the two interceptors that apply them. Exercised by tests, not by
    a UI that races.
-   **Half working:** offline mode. Going offline works — subtrees re-render
    locally from the parked snapshot and dispatches keep running. Coming
    back doesn't: the <a href="#org0022b72">action</a> log ships un-encoded and the receiving
    handler's decode is commented out.  `select-paths` is a blunt API and
    `cache-queries` is collected but never read.  The <a href="#org6a372c1">CSR</a> handoff is in
    the same state: prefetch and hook wired, takeover not.
-   **Seeded, incomplete:** dependency tracing (`🌿/q`, `🌿/trace`, `::🌿/deps`) —
    the runtime trace works, the static registry behind `defc=/=defq`
    isn't wired.
-   **Built, but in a different repo:** the <a href="#orga8019e9">divert</a> atlas and
    `offworld-atlas-mode`. The classifier reads Offworld's `staging`
    namespace directly and arguably belongs here. It's an editing aid,
    not a gate — nothing in CI re-emits the faces table or fails on a
    staging violation.
-   **Missing, and wanted:** a distinguishable representation for
    placeholders, so an unresolved one fails loudly instead of passing
    as data.
-   **Speculative:** compiling statically-known dispatches to direct client
    code.


<a id="org762cf55"></a>

# Glossary


<a id="org92bf50d"></a>

## Terms from supporting libraries

-   **<a id="org788aa9e">Replicant</a>:** renders Clojure data structures to DOM, and hands you
    one hook where every interaction arrives, plus hooks for elements
    appearing and leaving.  No component model.
-   **<a id="orgd625364">Nexus</a>:** a table of named handlers, and a loop that drains a list of
    things to do by looking each one up as it goes. Its own vocabulary —
    <a href="#org0022b72">action</a>, <a href="#org845b937">dispatch</a>, <a href="#orge983817">expansion</a>, <a href="#org8109de2">effect</a>, <a href="#orgde0a8bf">placeholder</a>, <a href="#org5337c23">interpolation</a>,
    <a href="#org86ec34f">interceptor</a> — appears below in one line each; <a href="#orgd625364">Nexus</a> defines them at
    length<sup><a id="fnr.nexus_nomenclature.5" class="footref" href="#fn.nexus_nomenclature" role="doc-backlink">5</a></sup>.
-   **<a id="org9d6c43d">Datastar</a>:** attribute-driven client behaviour, and DOM *morphing*
    over *<a href="#org19af95e">SSE</a>* — both below.
-   **<a id="orgd0bf6c5">hiccup</a>:** Clojure vectors as markup — `[:button {:class "x"}
      "Click"]`. <a href="#org788aa9e">Replicant</a> renders it.
-   **<a id="org6dc795d">morph</a>:** patching an existing DOM tree in place to match a new HTML
    fragment, keeping the nodes that didn't change. <a href="#org9d6c43d">Datastar</a>'s
    alternative to replacing a subtree.
-   **<a id="org19af95e">SSE</a>:** server-sent events. One long-lived HTTP response the server
    keeps writing to, so it can push without being asked.
-   **<a id="org0022b72">action</a>:** one `[:keyword arg arg]` vector. <a href="#orgd625364">Nexus</a>'s unit of work.
-   **<a id="org845b937">dispatch</a>:** handing <a href="#orgd625364">Nexus</a> a vector of actions and letting it drain
    them.
-   **<a id="orge983817">expansion</a>:** a handler that receives an <a href="#org0022b72">action</a> and returns *more
    actions*. Where decisions live. (<a href="#orgd625364">Nexus</a> also calls these <a href="#org0022b72">action</a>
    handlers.)
-   **<a id="org8109de2">effect</a>:** a handler at the end of the chain. It mutates something
    and returns nothing useful.
-   **<a id="orgde0a8bf">placeholder</a>:** a vector standing for a value that doesn't exist yet,
    like `[:event.target/value]`.
-   **<a id="org5337c23">interpolation</a>:** walking an <a href="#org0022b72">action</a> tree and replacing every
    <a href="#orgde0a8bf">placeholder</a> with the value it stands for.
-   **<a id="org86ec34f">interceptor</a>:** a function <a href="#orgd625364">Nexus</a> calls at a named point in its loop —
    before a <a href="#org845b937">dispatch</a>, before each <a href="#org0022b72">action</a>, after each <a href="#org8109de2">effect</a> — which can
    inspect or rewrite what's passing through.


<a id="orgfb99255"></a>

## Terms from Offworld

-   **<a id="org1cb1fa9">intent</a>:** a whole `:on` vector: the actions one user gesture
    means. The unit you author.
-   **<a id="orgbae0691">staged computation</a>:** an interaction written as one value whose
    parts are evaluated at different stages, rather than as code that
    runs all at once.
-   **<a id="org1925853">stage</a>:** one link in a <a href="#orgbae0691">staged computation</a>. There are five —
    render, <a href="#org6dc795d">morph</a>, client, request, server. Only *client* and *server* hold
    handlers; the rest are the authoring site and the two transports.
-   **<a id="org5bfa015">world-tag</a>:** `^::🪐/server` or `^::🪐/client` metadata on a
    handler, naming its <a href="#org5617283">world</a>. Unmarked means client.
-   **<a id="org5617283">world</a>:** which machine a handler belongs to — 🌍server or 🪐client.
    Offworld's primary axis of classification.
-   **<a id="orga8019e9">divert</a>:** the client-side split. It runs the client half of a
    <a href="#org845b937">dispatch</a> locally and hands the server half back to <a href="#org9d6c43d">Datastar</a>.
-   **<a id="org0377cd1">stem</a>:** the whole application state, passed down to a render-fn as
    an argument, alongside a path saying where that render-fn lives
    inside it.
-   **<a id="orgf27679f">SSR</a> / <a id="org6a372c1">CSR</a>:** server-side and client-side rendering. Offworld runs
    the same code in both, plus an offline third mode.


<a id="orgf6660ca"></a>

## Terms from the literature

-   **<a id="org70920cb">closure</a>:** a function together with the bindings it captured where
    it was written. The captured half is why one can't be sent over a
    wire.
-   **<a id="orgc6af93c">defunctionalization</a>:** replacing a function value with a name plus
    the values it captured, and one dispatcher that applies the name to
    them. Reynolds, 1972.
-   **<a id="org5623687">ambient authority</a>:** the power to affect something you never named
    and can't see.
-   **<a id="org9b814ba">residual state</a>:** the small presentational facts a UI accumulates
    that nothing else needs — is this disclosure open, what is
    half-typed in this box.
-   **<a id="org30a964a">cursor</a>:** a read/write handle onto one path inside a larger value,
    so the holder can get and set without knowing where it is.
-   **<a id="orgffa6465">signal</a>:** a mutable cell that notifies dependents when it
    changes. The reactive-graph primitive Offworld doesn't use.
-   **<a id="org48ba48b">prop drilling</a>:** passing state down through every intermediate call,
    so a render-fn's arguments are its whole scope.
-   **<a id="org78e40cc">late binding</a>:** choosing what a name refers to when it is used
    rather than when it is written. Independent of *when* it runs — a
    synchronous call can be late-bound.
-   **<a id="org407ef34">refunctionalization</a>:** the inverse of <a href="#orgc6af93c">defunctionalization</a>: turning
    the tag plus its values back into a direct call. What a compiler
    could do to an <a href="#org1cb1fa9">intent</a> it can see whole.
-   **<a id="orgab3ec45">Greenspun's tenth rule</a>:** the joke that every large program contains
    an ad-hoc, informally-specified implementation of half of
    Lisp. Invoked whenever someone builds an interpreter without meaning
    to.


<a id="orgf1f006c"></a>

# Sources

-   [Replicant](https://replicant.fun/) — rendering, life-cycle hooks, [JS interop](https://replicant.fun/tutorials/javascript-interop/) and per-node
    memory
-   [Nexus](https://github.com/cjohansen/nexus) — actions, effects, placeholders
-   [Datastar](https://data-star.dev/) — attributes and <a href="#org19af95e">SSE</a> morphing
-   [re-frame](https://day8.github.io/re-frame/) — the unidirectional loop this is downstream of, and its
    own honest writing on the cost of an execution model
-   [hyperlith](https://github.com/andersmurphy/hyperlith) — the same <a href="#org9d6c43d">Datastar</a> substrate, different answers; batched
    rendering and work sharing above all

Everything else this document leans on is cited where it is used, in
the footnotes below.


# Footnotes

<sup><a id="fn.1" href="#fnr.1">1</a></sup> <https://bladerunner.fandom.com/wiki/Off-world_colonies>

<sup><a id="fn.2" href="#fnr.2">2</a></sup> [Definitional Interpreters for Higher-Order Programming Languages](https://dl.acm.org/doi/10.1145/800194.805852)
(1972), where <a href="#orgc6af93c">defunctionalization</a> is introduced. For the modern
treatment, [CPS, defunctionalization, accumulations and associativity](https://arxiv.org/pdf/2111.10413),
Danvy et al.

<sup><a id="fn.3" href="#fnr.3">3</a></sup> It is string <a href="#org5337c23">interpolation</a>, but as a single dumb transport rather than
code generation: an opaque blob in one fixed slot, beside a <a href="#org845b937">dispatch</a>
URL that is constant for the whole app, and JavaScript around it that
is byte-identical on every element. Compare the usual <a href="#org9d6c43d">Datastar</a>
pattern, where each expression is customized by hand or by codegen,
each one inventing its own mini-language for carrying values and
procedure calls across a <a href="#org1925853">stage</a> boundary.

<sup><a id="fn.4" href="#fnr.4">4</a></sup> Push on every state change; push on a fixed interval and only when the
render differs; throttle to a frame; re-render only the subtree that
changed. The interval answer is also where write-coalescing belongs —
see [Effect](#org97ae3a9).

<sup><a id="fn.5" href="#fnr.5">5</a></sup> [Nexus: Nomenclature](https://github.com/cjohansen/nexus#nomenclature) — <a href="#org0022b72">action</a>, <a href="#orge983817">expansion</a> handler, <a href="#org8109de2">effect</a>, <a href="#org8109de2">effect</a>
handler, system, state, <a href="#org845b937">dispatch</a> data, <a href="#orgde0a8bf">placeholder</a>, `nexus`, `ctx`.

<sup><a id="fn.6" href="#fnr.6">6</a></sup> Hyperlith calls that interval a resolution window — "Batching pairs
really well with CQRS as you have a resolution window, this defines the
maximum frequency the view can update" — and calls rendering once for
every connected client "work sharing".
([hyperlith](https://github.com/andersmurphy/hyperlith))

<sup><a id="fn.7" href="#fnr.7">7</a></sup> The carry clause is the [rule of least power](https://www.w3.org/2001/tag/doc/leastPower.html) applied per value: the
weaker notation is the one you can analyse, and a reference held as
opaque data stays analysable in a way a reference already computed
with does not.

<sup><a id="fn.8" href="#fnr.8">8</a></sup> Require `nextjournal.offworld.guard` from code the client bundle
loads, or the registration never runs there. `register-standard-nexus!`
does it for you.

<sup><a id="fn.9" href="#fnr.9">9</a></sup> This is an informal binding-time analysis — the static / dynamic split
of [Partial Evaluation and Automatic Program Generation](https://www.itu.dk/people/sestoft/pebook/), Jones, Gomard
& Sestoft.

<sup><a id="fn.10" href="#fnr.10">10</a></sup> [Datomic: database filters](https://docs.datomic.com/reference/filters.html) — `basis-t` and `as-of`, a coordinate into
history rather than a snapshot store.

<sup><a id="fn.11" href="#fnr.11">11</a></sup> [Enterprise Integration Patterns: Claim Check](https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html) — store the payload,
pass a token, redeem it later.

<sup><a id="fn.12" href="#fnr.12">12</a></sup> [Nexus: Interceptors](https://github.com/cjohansen/nexus#interceptors) — the six phases (`:before-dispatch`,
`:after-dispatch`, `:before-action`, `:after-action`, `:before-effect`,
`:after-effect`) and what each one is handed.

<sup><a id="fn.13" href="#fnr.13">13</a></sup> <https://gist.github.com/JuneKelly/57b1acd4234409917d44eb90c88d7804#file-baselinetest-txt-L149-L151>

<sup><a id="fn.14" href="#fnr.14">14</a></sup> [re-frame-10x](https://github.com/day8/re-frame-10x) — a visual REPL for exactly this problem: which
subscriptions a view actually read, and what changed between renders.

<sup><a id="fn.15" href="#fnr.15">15</a></sup> [Out of the Tar Pit](https://curtclifton.net/papers/MoseleyMarks06a.pdf) — Moseley & Marks, 2006, on accidental complexity
and how much of it comes from state and control.

<sup><a id="fn.16" href="#fnr.16">16</a></sup> `🌿/q` and `🌿/trace` record a call tree at runtime. The static registry
`::🌿/deps` is aiming at isn't finished.

<sup><a id="fn.17" href="#fnr.17">17</a></sup> The case against announcement-by-bubbling is made best from inside
component frameworks that offer it: [LWC: event propagation](https://developer.salesforce.com/docs/platform/lwc/guide/events-propagation.html) and [How
events bubble in LWC](https://developer.salesforce.com/blogs/2021/08/how-events-bubble-in-lightning-web-components) on what it costs a component's API;
[Angular: avoiding custom event bubbling](https://blog.angular-university.io/angular-component-design-how-to-avoid-custom-event-bubbling-and-extraneous-properties-in-the-local-component-tree/) on the mirror-image problem in
a framework without cheap threading; [composed: true considered
harmful?](https://dev.to/open-wc/composed-true-considered-harmful-5g59) (Westbrook Johnson, 2019); [Shadow DOM and event propagation](https://pm.dartus.fr/posts/2021/shadow-dom-and-event-propagation/)
on retargeting and `composedPath()` as an "escape hatch to the shadow
DOM encapsulation model". For delegation as pure transport,
[Internals of event delegation](https://blog.logrocket.com/deep-internals-event-delegation/).

<sup><a id="fn.18" href="#fnr.18">18</a></sup> <a href="#org78e40cc">Late binding</a>, zero-or-many callees, error isolation, and
name-decoupling. Not time-decoupling: the listeners run synchronously
on the caller's own stack, so a bubbled event defers nothing a direct
call couldn't.

<sup><a id="fn.19" href="#fnr.19">19</a></sup> "Events from portals propagate according to the React tree rather than
the DOM tree." ([React: createPortal](https://react.dev/reference/react-dom/createPortal))

<sup><a id="fn.20" href="#fnr.20">20</a></sup> [hashp](https://github.com/weavejester/hashp) — a data reader that prints the form, its location and its
value, then returns the value unchanged, so it can sit anywhere inside
an expression.

<sup><a id="fn.21" href="#fnr.21">21</a></sup> [re-frame: guiding philosophy](https://day8.github.io/re-frame/historical/#guiding-philosophy) — the primacy of data, and the case
against read/write cursors and two-way binding.
