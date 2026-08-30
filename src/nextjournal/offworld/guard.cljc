(ns nextjournal.offworld.guard
  (:require
   [nextjournal.offworld :as-alias 🪐]))

(def guard ^::🪐/client
  (fn [_state pred actions]
    (assert (or (empty? actions) (vector? (first actions)))
            "guard takes a vector of actions, as in [::guard pred [[:a] [:b]]]")
    (if pred (vec actions) [])))
