(ns nextjournal.offworld.conn
  (:require
   [nextjournal.offworld :as-alias 🪐]))

(defonce !store (atom {}))

(def ^:dynamic *conn-id* nil)

(defn id [dispatch-data] (::🪐/conn-id dispatch-data))

(defn stash!
  "Hold `v` for the current connection and return an opaque token for it. Returns
  a token either way; with no connection bound there is nothing to redeem it
  against, which is what makes a token from one connection useless in another."
  [v]
  (let [tok (str (random-uuid))]
    (when *conn-id* (swap! !store assoc-in [*conn-id* tok] v))
    tok))

(defn intern!
  "Like `stash!`, but a value already held keeps the token it was given.

  A render-time hoist happens on every render, so minting per call would grow
  the store with the render count rather than with the number of distinct things
  held. Keyed by the value, so what bounds the store is how many different
  things a connection has referred to."
  [v]
  (if-let [tok (get-in @!store [*conn-id* :by-value v])]
    tok
    (let [tok (str (random-uuid))]
      (when *conn-id*
        (swap! !store update *conn-id*
               #(-> % (assoc-in [:by-value v] tok) (assoc tok v))))
      tok)))

(defn fetch [conn-id token] (get-in @!store [conn-id token]))

(defn release! [conn-id] (swap! !store dissoc conn-id))
