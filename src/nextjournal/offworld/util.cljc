(ns nextjournal.offworld.util
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [ring.util.codec :as codec]
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]))

(defn serialize [actions]
  (binding [*print-meta* true]
  (-> (pr-str actions)
      (str/replace  "\"" "%22"))))

(defn deserialize [s]
  (-> s
      (str/replace  "%22" "\"")
      edn/read-string))

#?(:clj (defn read-dispatch [{:keys [query-string]}]
          (some-> query-string
                  codec/form-decode
                  (get "datastar")
                  cheshire/parse-string
                  walk/keywordize-keys
                  (update :actions deserialize))))

(defn select-paths [m paths]
  (reduce #(assoc-in %1 %2 (get-in m %2)) {} paths))

(defn priority-sorted-map
  [priority-keys]
  (let [rank         (zipmap priority-keys (range))
        default-rank (count priority-keys)]
    (sorted-map-by
     (fn [a b]
       (let [ra (get rank a default-rank)
             rb (get rank b default-rank)]
         (if (= ra rb)
           (compare a b)
           (compare ra rb)))))))
