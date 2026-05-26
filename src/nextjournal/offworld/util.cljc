(ns nextjournal.offworld.util
  #?(:clj (:require
           [clojure.string :as str]
           [ring.util.codec :as codec]
           [cheshire.core :as cheshire])))

(defn serialize [actions])

(defn deserialize [s])

#?(:clj (defn read-dispatch [{:keys [query-string]}]
          (some-> query-string
                  codec/form-decode
                  (get "datastar")
                  cheshire/parse-string
                  (get "offworld")
                  deserialize)))

#?(:clj (defn read-action-log [{:keys [query-string]}]
          (some-> query-string
                  codec/form-decode
                  (get "action-log")
                  deserialize)))

(defn select-paths [m paths]
  (reduce #(assoc-in %1 %2 (get-in m %2)) {} paths))

#?(:clj (defn fn-ref->str [x]
          (->> (meta x)
               ((juxt :ns :name))
               (clojure.string/join "/"))))
