(ns nextjournal.offworld.demo.offline
  (:require
   [nextjournal.offworld.util :as ou]
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as-alias 🪐]))

(defn offline-capable
  [{:keys    [id render-fn select-paths config cache-queries]
    ::k/keys [path stem]} hiccup]
  (let [sync-state {:id            id
                    :cache-queries (zipmap (map ou/fn-ref->str cache-queries)
                                           (for [q cache-queries] (q stem))) ;; TODO get these from `defc`'s static trace?
                    :cache-config  config
                    :select-paths  select-paths                              ;; TODO rethink this api?
                    :render-fn     (ou/fn-ref->str render-fn)
                    ::k/stem       (ou/select-paths stem (into [] (concat select-paths [path])))
                    ::k/path       path}]
    (def sync-state  sync-state)
    [:div {:id                 id
           :data-offworld-sync (ou/encode sync-state)}
     hiccup]))
