(ns nextjournal.offworld.standard
  (:require
   [nextjournal.offworld.claim :as-alias 🎫]
   [nextjournal.offworld.claim :as claim]
   [nextjournal.offworld.guard :as-alias 🚦]
   [nextjournal.offworld.guard :as guard]
   [nextjournal.offworld.expr :as expr]
   [nextjournal.offworld.inline :as-alias inline]
   [nextjournal.offworld.inline :as inline-impl]
   [nextjournal.offworld.nexus.registry :as reg]
   [nextjournal.offworld.stem :as-alias 🌿]
   [nextjournal.offworld.stem :as stem]))

(def standard-nexus
  {:nexus/expansions   {::🚦/guard      guard/guard
                        ::inline/choose expr/choose}
   :nexus/placeholders {::🌿/el         stem/el
                        ::🎫/value      claim/value
                        ::inline/expr   expr/expr}
   :nexus/effects      {::inline/invoke inline-impl/invoke
                        ::inline/expr!  expr/expr!}})

(defn register-standard-nexus! [] (reg/register-many! standard-nexus))
