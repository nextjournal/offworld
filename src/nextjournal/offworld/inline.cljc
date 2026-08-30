(ns nextjournal.offworld.inline
  (:require
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.conn :as conn])
  #?(:cljs (:require-macros [nextjournal.offworld.inline :refer [inline]])))

#?(:clj (def ^:dynamic *conn-id* nil))

#?(:clj
   (defn register! [f]
     (binding [conn/*conn-id* (or *conn-id* conn/*conn-id*)]
       (conn/stash! f))))

#?(:clj (defn release! [conn-id] (conn/release! conn-id)))

#?(:clj
   (defmacro inline [& body]
     `[[::invoke (register! (fn [] ~@body))]]))

(def invoke ^::🪐/server
  (fn [ctx system tok]
    #?(:clj
       (let [conn-id (or (conn/id (:dispatch-data ctx)) (::🪐/conn-id @system))]
         (when-let [f (conn/fetch conn-id tok)] (f))))))
