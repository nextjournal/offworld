(ns nextjournal.offworld.util
  (:require
   #?@(:cljs [[nextjournal.offworld.transit-lite :as tx]])
   #?@(:clj [[clojure.string :as str]
             [ring.util.codec :as codec]
             [cheshire.core :as cheshire]
             [cognitecht.transit :as tx]]))
  #?(:clj (:import (java.io ByteArrayInputStream ByteArrayOutputStream))))


(defn serialize [data]
  #?(:clj
     (let [baos (ByteArrayOutputStream.)
           w    (tx/writer baos :json {:transform tx/write-meta})
           _    (tx/write w data)
           ret  (.toString baos "utf-8")]
       (.reset baos)
       ret)
     :cljs
     (tx/write-str (tx/write-meta data))))

(defn deserialize [s]
  #?(:clj
     (let [bais   (ByteArrayInputStream. (.getBytes s))
           reader (tx/reader bais :json)]
       (tx/read reader))
     :cljs
     (tx/read-str s)))

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
