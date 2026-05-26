(ns nextjournal.offworld.nexus.registry
  (:require [nexus.registry :as nxr])
  (:require-macros [nextjournal.offworld.nexus.registry
                    :refer [register-action!]]))

(defmacro register-action! [k handler]
  (let [sym (gensym)]
  `(do
     (def ^{::handler true} ~sym ~handler)
     (nxr/register-action! ~k ^{::handler true} #'~sym))))

(macroexpand-1
 '(register-action! :x (fn [] :HI)))

(nxr/register-action! :x ^{::handler true} (fn [] :HI))
