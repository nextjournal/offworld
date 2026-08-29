(ns nextjournal.offworld.guard
  (:require
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]))

(nxr/register-expansion! ::guard ^::🪐/client
  (fn [_state pred & actions]
    (if pred (vec actions) [])))
