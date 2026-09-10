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

(defn offer!
  "Record that `token` was rendered for the current connection.

  A content address is a name for code, and code is permanent and shared, so the
  address alone cannot say who may run it. What is per-connection is not the
  body but the *offer*: the server rendered this one into this page, and that is
  the thing an arriving dispatch has to match."
  [token]
  (when *conn-id*
    (swap! !store update-in [*conn-id* :offered] (fnil conj #{}) token))
  token)

(defn offered?
  "Whether `token` was rendered for `conn-id`."
  [conn-id token]
  (contains? (get-in @!store [conn-id :offered] #{}) token))

(defn fetch [conn-id token] (get-in @!store [conn-id token]))

(defn release! [conn-id] (swap! !store dissoc conn-id))
