(ns nextjournal.offworld.util
  (:require
   #?@(:cljs [[nextjournal.transit-lite :as tx]])
   #?@(:clj [[clojure.string :as str]
             [ring.util.codec :as codec]
             [cheshire.core :as cheshire]
             [cognitect.transit :as tx]]))
  #?(:clj (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
                   (java.util Base64))))

(defn encode [data]
  #?(:clj
     (let [baos (ByteArrayOutputStream.)
           w    (tx/writer baos :json {:transform tx/write-meta})
           _    (tx/write w data)
           ret  (.toString baos "utf-8")]
       (.reset baos)
       (.encodeToString (Base64/getEncoder) (.getBytes ret "utf-8")))
     :cljs
     (js/btoa (tx/write-str (tx/write-meta data)))))

(defn decode [s]
  #?(:clj
     (let [decoded (String. (.decode (Base64/getDecoder) s) "utf-8")
           bais    (ByteArrayInputStream. (.getBytes decoded "utf-8"))
           reader  (tx/reader bais :json)]
       (tx/read reader))
     :cljs
     (tx/read-str (js/atob s))))

#?(:clj (defn read-dispatch [{:keys [query-string]}]
          (some-> query-string
                  codec/form-decode
                  (get "datastar")
                  cheshire/parse-string
                  (get "offworld")
                  decode)))

#?(:clj (defn read-action-log [{:keys [query-string]}]
          (def query-string query-string)
          (some-> query-string
                  codec/form-decode
                  (get "action-log")
                #_  decode)))

(defn select-paths [m paths]
  (reduce #(assoc-in %1 %2 (get-in m %2)) {} paths))

#?(:clj (defn fn-ref->str [x]
          (->> (meta x)
               ((juxt :ns :name))
               (clojure.string/join "/"))))
