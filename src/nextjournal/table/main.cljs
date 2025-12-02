(ns nextjournal.table.main
  (:require [nexus.core :as nexus]
            [nextjournal.table.ui :as ui]
            [replicant.dom :as r]))




(def !state
  (atom {}))

(def nexus
  {:nexus/system->state deref
   :nexus/effects {:effects/save (fn save [_ store path value]
                                   (swap! store assoc-in path value))}
   :nexus/actions {:actions/inc (fn inc [state path]
                                  [[:effects/save path (+ (:step state) (get-in state path))]])}
   :nexus/placeholders {:event.target/value (fn event-target-value [{:replicant/keys [dom-event]}]
                                              (some-> dom-event .-target .-value))
                        :fmt/as-long (fn fmt-as-long [_ value]
                                       (or (some-> value parse-long) 0))
                        :fmt/as-double (fn fmt-as-double [_ value]
                                         (or (some-> value parse-double) 0.0))}})

(r/set-dispatch! #(nexus/dispatch nexus !state %1 %2))

(defonce root-el
  (js/document.getElementById "app"))

(defn main []
  (add-watch !state ::render (fn [_ _ _ new-state]
                               (r/render root-el (ui/render new-state))))

  ;; Trigger the initial render
  (reset! !state {}))

