# Offworld, distilled

A short pass over `readme.org` for a reader who already knows Clojure, closures,
defunctionalization, and why re-frame ended up where it did. `readme.org` is the
source; this is a reduction, not an authority.

Emoji in code are namespace aliases, nothing more: `🪐` offworld, `🌿` stem,
`🚦` guard, `📈` order, `🌠` offline.

## Thesis

A user intent is a staged computation split across two worlds. Express it as a
plain vector of actions, each tagged with the world it runs in.

```clojure
[:button {:on {:click [[::pan-to id 4.89 52.38]
                       [:effects/save path {:center [4.89 52.38]}]]}}
 "Amsterdam"]

(nxr/register-effect! ::pan-to      ^::🪐/client (fn [ctx _ id lng lat] ...))
(nxr/register-effect! :effects/save ^::🪐/server (fn [_ system path v] ...))
```

One click, one vector, two runtimes. The map moves in the browser; the position
saves on the server. Where each part runs follows from the metadata on its
handler — not from a rewrite, a second language, or a naming convention.

The same code renders three ways: server-rendered and morphed over SSE, rendered
entirely client-side by Replicant, or rendered offline from a snapshot the server
parked in the markup. SSR vs CSR is a runtime decision, not an authoring one.

## Why data

Write the handler you actually want and the problem states itself:

```clojure
[:button {:on {:click #(set-season! :winter)}} "Winter"]
```

Fine in a browser-only app. Render it on the server and the click happens later,
on another machine, and all the server can hand over is text. The closure's
captured bindings were never text and can't become text. So you leave the fn
where it can run, name it, and send the name with its arguments — Reynolds'
defunctionalization, not as a style choice but because nothing else crosses.

Every framework that runs one interaction across two machines does this. The only
real question is what you defunctionalize *into*: a string (an event name, a
payload key, JS pasted into an attribute), a place (a signal, a web-component
attribute whose meaning lives in a callback elsewhere), a macro expression (a
compiler infers which world each form belongs to), or a data structure.

Offworld picks data, for what data admits and the others don't. It crosses — a
vector is discrete, and looking up the head tells you the world at any depth. It
can be substituted into. It can be checked without running it. It can be
*extended by a caller that can't run it*, which is what makes render-fns reusable:
pass `:blue` to configure a button's colour, pass an intent to configure what its
click means, and the button restructures the intent without implementing it. It
can be logged and replayed. It can be signed.

The fair objection — a table of names you look functions up in is an interpreter,
and writing one is usually a mistake — is answered by observing that the
interpreter exists either way. Either it's Nexus, written down and identical in
both worlds, or it's an unwritten convention in a colleague's head.

## The ladder and the law

Five stages, read from one render to the next:

```
render → morph → client → request → server
```

Two hold handlers. `morph` and `request` are transports where nothing authored
resolves but time passes and messages can be lost, delayed, reordered or forged.
There is no finer rung *inside* a world, because Nexus drains to completion: an
expansion emitted by an expansion still expands, a placeholder that survives
expansion still meets interpolation before effects run.

**The law.** Computation authored at stage N may *consume* only values resolved by
some stage ≤ N. It may *carry* — nest, restructure, pass through — a reference to
a later-stage value as opaque data, but never compute with its contents early.

```clojure
[[::randomize [:event/key-modifiers] (get-season stem)]]
```

`(get-season stem)` is an ordinary call; it runs now and leaves `:spring`.
`[:event/key-modifiers]` is not a call — it names a value that doesn't exist yet
because the click hasn't happened. That's the whole notation: compute what you can,
name what you can't, and the name travels.

The same obligation shows up sideways. A reusable render-fn handed a `:click-ax`
by its caller may wrap or nest it and must not interpret it. Physics in the first
case, discipline in the second; one rule covers both.

Which gives the rule for when a branch must be data — and it isn't "whenever you
want a conditional", since expansions receive interpolated values and branch with
ordinary `if`:

**A branch has to be data when the value that decides arrives later than the code
that must choose.**

Two distances. Across the wire: a keydown handler that should commit on `Enter`
can't ask the server, because the answer arrives after the round trip it was
trying to avoid. Across a call: a button with distinct plain-click and alt-click
behaviour can't pick at render time, and mustn't look inside either alternative
to do it.

## One construct for that, deliberately

```clojure
(nxr/register-placeholder! ::enter?
  (fn [dd] (= "Enter" (.-key (:replicant/dom-event dd)))))

{:on {:keydown [[::🚦/guard [::enter?] [[::commit [:event.target/value]]]]]}}
```

`guard` is an ordinary client-side expansion: truthy and it expands to the actions
it was handed, falsey and it expands to nothing — and with no server action left,
no request goes out. The elision falls out of the existing machinery.

Note what isn't there: no `:not`, no `:and`, no `:cond`, no notation for comparison
at all. The first argument is a *value*, so the comparison lives inside a
placeholder and the negation is a different argument. Anything genuinely logical
goes in an expansion you register and branches in plain Clojure. The guard is the
zero-registration version of that, kept too weak to be worth reaching for when a
handler would do — and if it still reads as the top of a slope, don't use it:
register `[::commit-on-enter path]` and the branch is ordinary Clojure inside your
own handler.

## Binding time, per value

`[[:effects/save path (inc n)]]` freezes a number at render. `[[::bump path]]`
names an operation that recomputes at dispatch. Ten clicks faster than the server
re-renders land on `1` in the first spelling and `10` in the second — same button,
same registry, different thing carried.

Neither is right in the abstract. Stable identifiers — a path, a row id, a mode
name — are always safe early. It's "the value I read a moment ago" that freezes
whether you meant it to or not, and it bites when the render loop lags the next
click, is deliberately capped below click speed, or the dispatch source isn't a
person: a drag handler, key repeat, a script.

An early value too large to inline has four carriers, most legible first: inline
it; carry a coordinate into immutable history (Datomic's `basis-t`) and re-read
`as-of` it at dispatch, retaining nothing; stash it server-side behind a
claim-check token; or hand over the closure, everything pinned and nothing legible.
Offworld ships three — the second is design rather than code, and only exists if
your data layer can name its own past. That's the untested claim under the whole
ladder: the snapshot machinery a UI framework must invent is inversely proportional
to how addressable its data layer is.

## What a machine can check

Two violations, both the *backward* case where a carry has nowhere left to go:
`:stranded-client-ref` (a client-world reference that survived past `request` into
server-bound actions) and `:unregistered-action` (a dispatched key resolving to no
handler, otherwise a silent no-op). The forward case isn't decidable and Offworld
doesn't guess — whether a handler consumes or carries lives in its body.

The real check is a Nexus `:before-action` interceptor, so it sees every action at
any expansion depth, including ones an expansion computed, a caller injected, or a
guard nested. The same checks are pure fns you can run over source as a lint, with
a lint's coverage: a clean static pass means no violations *among the ones that
could be read*, never no violations.

It works at all because a dispatch is a tree of keyword-headed vectors whose keys
carry the world tag, so the analysis is `tree-seq`, `get-in`, `meta` — never
running a handler. Hand it a closure and there is nothing to walk.

## State: the stem

Prop drilling gives honest signatures and re-drilling on every tree change. Global
state with a query registry is concise and hides a render-fn's real dependencies.
With top-down rendering you can have both, because the global state can just *be*
one of the arguments — immutable data means you're passing a pointer.

The stem is the whole state carried down as a value, plus a path saying where this
render-fn lives in it. `🌿/+` extends the path and merges configuration, `🌿/local`
reads this subtree back out. Three concerns stay apart: configuration is plain
keys, local state is reached at the path, domain state is read with an ordinary
getter.

`🌿/id` derives from the path, so an element's name comes from its position in the
render composition — not a mount counter or a place in the DOM. That's what makes
it survive morphing. The `::🌿/el` placeholder resolves a path back to a live node
at dispatch time, which is the whole of what a component framework's refs are for.

## Why no bubbling

The DOM event system fuses delegation (transport), default-action negotiation (the
browser's own channel), and announcement (an inner element throws, ancestors
subscribe). Offworld keeps the first two and declines the third.

Delegation's usual motivation doesn't apply, because listeners are attributes in
rendered markup and ship with the DOM on every morph — nothing is attached
imperatively to be lost. Announcement costs an obligation: every consumer along
the path must understand the event, and `stopPropagation` hands a leaf ambient
authority to silence handlers it has never heard of. And bubbling routes by
containment, so wrapping something in a div silently changes who hears an intent.

Instead the ids are passed in and the intent names its recipients:

```clojure
"Enter" [[:event/prevent-default]
         [:node/hide-popover [::🌿/el popover-id]]
         [:node/blur [::🌿/el anchor-id]]
         [:node/set-checked [::🌿/el choice-id] true]
         [:effects/conj (conj path :filters) (first filters-to-add) #{}]]
```

`[:event/prevent-default]` sits in the same vector, so the event was never needed
as an architecture, only as a transport. The failure modes differ too: a missing
injected id resolves to nothing and can be asserted on, where a bubbled event with
no listeners is indistinguishable from one that worked.

The real discriminator isn't subscriber count — any consumer set derivable at
render time is injectable. It's authorship: **bubbling is the interop protocol
with code that isn't in your render tree.** Inside it, injection does the same job
more safely.

## Per-frame work

A drag fires `pointermove` sixty times a second and every one is a dispatch.
Sample client actions freely; commit to the server once.

```clojure
{:on {:pointerdown [[::rz/grab]]
      :pointermove [[::🚦/guard [::rz/dragging?] [[::rz/preview grid-id idx]]]]
      :pointerup   [[::🚦/guard [::rz/dragging?]
                     [[::rz/commit path c [::rz/width grid-id idx]]]]]}}
```

The generated attribute is `_sp && @get(…)`, so a dispatch that resolves entirely
in the browser never calls out. Sixty dispatches, one request. Note which value
crosses: the width is never accumulated during the drag — the preview writes the
DOM, and `[::rz/width grid-id idx]` reads it back at the last moment before the
action leaves, so no drag state lives anywhere.

One thing this doesn't settle. Between the commit and the morph that confirms it,
something must own the property the gesture was changing and keep re-asserting it
across any morph landing in the gap. Offworld has places to hold a client-owned
value and no vocabulary at all for the *handoff* — when the client may stop
asserting. Hand-rolled per gesture today, and the one part of a rich seam this
design doesn't make legible.

## Offline, which is where this stops being aesthetic

An SSR page that loses its connection is normally dead. `offline-capable` wraps a
subtree, encodes the paths it actually needs, and parks them in an attribute. On
the `offline` event Offworld rebuilds a stem from the parked paths, flips to CSR,
and re-renders those subtrees from the same render-fns with no server. Dispatches
keep working: server-bound actions append to an action log, everything else runs
locally. Reconnecting ships the log for the server to reconcile — replay by intent,
not a diff of two states.

Going offline works. Coming back doesn't: the log ships un-encoded and the
receiving decode is commented out.

## Objections

**Greenspun?** Possibly, but a careful look at SSR shows some reified procedure is
unavoidable. The question isn't whether you built an abstract machine, it's whether
you admit it.

**Why not infer the boundary with macros?** Hyper and Electric do versions of this
with real ergonomic wins. What they don't leave you with is a value — nothing to
walk, check, tag or log, and a changed intent shows up as new compiler output.

**Isn't shipping data slower than shipping code?** Two costs that kept getting
confused; measuring a large table separated them. On the wire, compression absorbs
it — inline intents are enormously repetitive, the case compression handles best,
and templating or interning them buys only a marginal further reduction. On parse,
those replacements don't help at all: the browser decompresses back to full text
and parse time tracks the uncompressed byte count, which they barely move. The
opaque reference is the smaller one, not the faster one.

**Why not signals?** A signal is a defunctionalized setter on a mutable place —
stringly-typed, meaning in a callback rather than on the wire. Fine for residual
state that wants a cell; poor for intent, which wants to be logged, replayed and
carried across a boundary. Read the critique narrowly: "not data" isn't the same as
"a place", and a client-side capability addressed by an id is neither.

**Why not cursors?** A cursor fuses read and write onto one path, and Offworld
already hands you both halves separately. Fusing them defunctionalizes the *place*
rather than the *intent*, and the two aren't recoverable from each other: from
`[:effects/save [:panel :season] :winter]` you can't tell someone shift-clicked
Randomize, but from `[::randomize [:shift] :spring]` you can recover both. That
decides three things once a write leaves the process — you can ask whether a user
may randomize the season but not meaningfully whether they may set a path to a
value; replaying places overwrites where replaying intents re-decides; and a middle
layer can restructure an intent it doesn't understand where a write it can only
intercept. Mechanically, a cursor is a closure over a path, so it can't cross the
wire, and defunctionalizing it gives you back `[:effects/save path v]`.

The usual command-query framing is the weaker version of this: a one-way system
dispatching `[:set-path [:a :b] 1]` everywhere has obeyed CQS and gained nothing.
The problem isn't that reads and writes travel together, it's that the write names
a location instead of a meaning. And the concession stands: for genuinely residual
state the intent *is* the place-write, and "you made me register an action to
toggle a boolean" is fair.

**Do I have to register everything?** No — `inline` registers a closure at render
time against the current connection and emits an invoke token. It costs every
property above: the wire carries a UUID, so there's nothing to check, colour,
authorize or replay, the token dies with the connection, and a fresh one mints per
render so the attribute churns on every morph. It also runs only on the server, so
it's no answer to the residual-client-state complaint.

## What it costs

**A call site doesn't show its staging.** Reading `[[:effects/save path v]
[::pan-to id 4.89 52.38]]`, nothing tells you the first runs on the server.
Legible as data, illegible as staging. Nothing is actually hidden — the registry
declares world and kind for every key — so it's a tooling gap, and largely a closed
one: a static atlas classifies every registration site and font-locks it into the
buffer. But classification is per key, not per dispatch.

**Placeholders are an ambient rule, and an unresolved one is invisible.** The
sharpest edge. Interpolation recognises a placeholder by looking a vector's head up
in the registry — any depth, any argument position. So one that fails to resolve,
through a typo or a stale client bundle, is indistinguishable from a vector you
meant to write; it travels on as data and nothing reports it. The tell is a value
arriving server-side as `["~:event.target/scroll-top"]` while the HTML looks
correct. It runs the other way too: interpolation runs again against the *server's*
registry, so an authored literal can be captured by a placeholder your code never
heard of. The checker can't help — head position is discriminated by structure,
argument position by registry membership, which is global and load-order dependent.
The honest fix is a distinguishable value, not a lint. Not done.

**Explicit state is still explicit.** The stem makes threading cheap, but a
render-fn needing three things still takes three things. That's the trade for
signatures that tell the truth, and it is a trade.

**And underneath all of it, this is an execution model.** Any library that decides
how an interaction is sequenced carries a risk its authors rarely spell out — the
lesson re-frame learned about its own machinery over a decade. The mitigation is to
keep it small enough to read end to end: three kinds of registration, one loop, one
metadata tag. Placeholder interpolation is the one rule that currently fails that
test.

## Status

Research library, not a product. Eleven files, under a thousand lines.

Solid: the dispatch split, placeholders, the world tag, SSR and CSR, the stem, the
staging analysis and its runtime checker, the guard, `inline`.

Implemented and tested with no worked example: both ordering policies (`:seq-gate`,
`:bounded-buffer`) and their interceptors — two plausible answers rather than two
proven ones.

Half working: offline (going works, coming back doesn't) and the CSR handoff
(prefetch and hook wired, takeover not).

Missing and wanted: a distinguishable representation for placeholders, so an
unresolved one fails loudly instead of passing as data.

Is it right for your app? Maybe not. If it's connected-only and CRUD-shaped, the
simplest thing that works is one global default — every interaction an opaque
server closure behind a minted key, no registry, no world tag, no staging
vocabulary. Designs of that shape run real production apps today.
