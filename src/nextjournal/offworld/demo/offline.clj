(ns nextjournal.offworld.demo.offline
  (:require
   [nextjournal.offworld.util :as ou]
   [nextjournal.offworld.stem :as 🌿]
   [nextjournal.offworld :as-alias 🪐]))

(defn offline-capable
  [{:keys    [id render-fn select-paths config cache-queries]
    ::🌿/keys [path stem]} hiccup]
  (let [sync-state {:id            id
                    :cache-queries (zipmap (map ou/fn-ref->str cache-queries)
                                           (for [q cache-queries] (q stem))) ;; TODO get these from `defc`'s static trace?
                    :cache-config  config
                    :select-paths  select-paths                              ;; TODO rethink this api?
                    :render-fn     (ou/fn-ref->str render-fn)
                    ::🌿/stem       (ou/select-paths stem (into [] (concat select-paths [path])))
                    ::🌿/path       path}]
    [:div {:id                 id
           :data-offworld-sync (ou/encode sync-state)}
     hiccup]))
