(ns nextjournal.offworld.inline
  (:require
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐])
  #?(:cljs (:require-macros [nextjournal.offworld.inline :refer [inline]])))

#?(:clj (defonce !closures (atom {})))

#?(:clj (def ^:dynamic *conn-id* nil))

#?(:clj
   (defn register! [f]
     (let [tok (str (random-uuid))]
       (when *conn-id*
         (swap! !closures assoc-in [*conn-id* tok] f))
       tok)))

#?(:clj
   (defn release! [conn-id]
     (swap! !closures dissoc conn-id)))

#?(:clj
   (defmacro inline [& body]
     `[[::invoke (register! (fn [] ~@body))]]))

(nxr/register-effect! ::invoke ^::🪐/server
  (fn [_ system tok]
    #?(:clj
       (when-let [f (get-in @!closures [(::🪐/conn-id @system) tok])]
         (f)))))
