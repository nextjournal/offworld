(ns nextjournal.offworld.util
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [clojure.edn :as edn]
   #?@(:clj [[ring.util.codec :as codec]
             [cheshire.core :as cheshire]])))

(defonce registry (atom {}))

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

#?(:clj (defn read-action-log [{:keys [query-string]}]
          (some-> query-string
                  codec/form-decode
                  (get "action-log")
                  deserialize)))

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

(defn fn-ref->str [x]
  (->> (meta x)
       ((juxt :ns :name))
       (clojure.string/join "/")))
