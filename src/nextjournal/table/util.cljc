(ns nextjournal.table.util
  (:require
   [clojure.string :as str]
   [nextjournal.ductile.load-builder :as load-builder]
   [nextjournal.baseline :as-alias k]))

(defn init-store
  []
  {::k/local
   {:grid {:size-cache (volatile! {})}}
   ::k/domain
   (load-builder/init-domain {})})
