(ns nextjournal.offworld.claim
  (:require
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.claim :as-alias 🎫]
   [nextjournal.offworld.conn :as conn]))

(defn stash!
  "Render-time. Hold `v` for this connection and return a reference that resolves
  back to `v` when the action carrying it reaches the server. The intent carries
  the token, never the value."
  [v]
  [::🎫/value (conn/stash! v)])

(defn hold!
  "Render-time. Like `stash!`, for a value the render will hand to a body it
  hoisted out of. Interned rather than minted, because the same value hoisted on
  every render is one thing held, not one per render."
  [v]
  [::🎫/value (conn/intern! v)])

(def value ^::🪐/server
  (fn [dispatch-data token]
    (conn/fetch (conn/id dispatch-data) token)))
