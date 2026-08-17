(ns nextjournal.offworld.nexus.registry
  (:require
   [nexus.registry :as nxr]))

(defn register-many! [m]
  (let [system->state (:nexus/system->state m)
        actions       (:nexus/actions m {})
        effects       (:nexus/effects m {})
        placeholders  (:nexus/placeholders m {})]
    (when system->state (nxr/register-system->state! system->state))
    (run! (fn [[k v]] (nxr/register-action! k v)) actions)
    (run! (fn [[k v]] (nxr/register-effect! k v)) effects)
    (run! (fn [[k v]] (nxr/register-placeholder! k v)) placeholders)))
