(ns nextjournal.table.main
  (:require
   [clojure.string :as str]
   [nexus.core :as nexus]
   [nextjournal.table.ui :as ui]
   [replicant.dom :as r]
   [nextjournal.table.ui.nested-grid :as-alias ng]))

(defonce !store
  (atom {:grid {:row-tree    (vec (take 500 (repeatedly #(keyword (gensym "r")))))
                :column-tree (vec (take 500 (repeatedly #(keyword (gensym "c")))))
                :size-cache  (volatile! {})}}))

(def nexus
  {:nexus/system->state deref
   :nexus/effects       {:effects/save (fn save [_ store path value]
                                         (swap! store assoc-in path value))}
   :nexus/actions       {:actions/inc (fn inc [state path]
                                        [[:effects/save path (+ (:step state) (get-in state path))]])
                         ::ng/scroll  (fn [_]
                                        [[:effects/save [:grid :scroll-top] [:event.target/scroll-top]]
                                         [:effects/save [:grid :scroll-left] [:event.target/scroll-left]]])}
   :nexus/placeholders  {:event.target/value       (fn event-target-value [{:replicant/keys [dom-event]}]
                                                     (some-> dom-event .-target .-value))
                         :event.target/scroll-top  (fn event-target-value [{:replicant/keys [dom-event]}]
                                                     (some-> dom-event .-target .-scrollTop))
                         :event.target/scroll-left (fn event-target-value [{:replicant/keys [dom-event]}]
                                                     (some-> dom-event .-target .-scrollLeft))
                         :fmt/as-long              (fn fmt-as-long [_ value]
                                                     (or (some-> value parse-long) 0))
                         :fmt/as-double            (fn fmt-as-double [_ value]
                                                     (or (some-> value parse-double) 0.0))}})

(r/set-dispatch! #(nexus/dispatch nexus !store %1 %2))

(defonce root-el
  (js/document.getElementById "app"))

(defn ^:dev/after-load after-load []
  (swap! !store update :dev/load inc))

(defn main []
  (when-not (str/includes? js/document.location.search "?ssr=true")
    (add-watch !store ::render (fn [_ _ _ new-state]
                                 (r/render root-el (ui/render new-state))))
    (after-load)))
