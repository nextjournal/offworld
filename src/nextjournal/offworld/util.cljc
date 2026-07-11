(ns nextjournal.offworld.util
  (:require
   ;; :refer of the protocol and its method slots works around a squint bug:
   ;; a deftype implementing an alias-qualified protocol from another ns
   ;; writes its methods under the wrong key, and with :refer the emitted
   ;; slot symbols are not imported
   #?@(:squint [[nextjournal.transit-lite :as tx
                 :refer [TransitTagged -tag -rep
                         TransitTagged__tag TransitTagged__rep]]]
       :cljs [[nextjournal.transit-lite :as tx]])
   #?@(:clj [[clojure.string :as str]
             [clojure.walk :as walk]
             [ring.util.codec :as codec]
             [cheshire.core :as cheshire]
             [cognitect.transit :as tx]]))
  #?(:clj (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
                   (java.util Base64))))

#?(:squint
   (deftype Keyword [fqn]
     Object
     (toString [_] fqn)
     TransitTagged
     (-tag [_] ":")
     (-rep [_] fqn)))

(defn- wire-kw
  "The value transit encodes as a keyword: a real keyword on hosts that have
  them, a TransitTagged keyword object on squint."
  [s]
  #?(:squint (Keyword. s)
     :default (keyword s)))

(def wire-args
  "Per action, argument positions that are not plain keyword data. :str
  leaves the argument untouched, :path treats it as a lookup path whose
  head is a keyword attribute and whose remaining segments are data.
  Every undeclared string in actions, trigger and lifecycle encodes as a
  keyword: keywords are the rule, strings the declared exception."
  {:effects/save {0 :path}
   :effects/conj {0 :path}
   :nextjournal.offworld.demo.ui.omnibox/add-filter    {0 :path 1 :str}
   :nextjournal.offworld.demo.ui.omnibox/remove-filter {0 :path 1 :str}})

(defn- kw-walk [x]
  (cond
    (string? x) (wire-kw x)
    (map? x) (update-vals x kw-walk)
    (vector? x) (mapv kw-walk x)
    :else x))

(defn- encode-action [[head & args]]
  (let [rules (get wire-args (keyword head))]
    (into [(wire-kw head)]
          (map-indexed (fn [i a]
                         (case (get rules i)
                           :str a
                           :path (into [(wire-kw (first a))] (rest a))
                           (kw-walk a))))
          args)))

(defn preprocess
  "Restores keyword types on the client dispatch payload before transit
  encoding. Only actions, trigger and lifecycle are typed; other entries
  pass through untouched."
  [payload]
  (if (map? payload)
    (cond-> payload
      (:actions payload)   (update :actions (partial mapv encode-action))
      (:trigger payload)   (update :trigger wire-kw)
      (:lifecycle payload) (update :lifecycle wire-kw))
    payload))

(defn encode [data]
  #?(:clj
     (let [baos (ByteArrayOutputStream.)
           w    (tx/writer baos :json {:transform tx/write-meta})
           _    (tx/write w data)
           ret  (.toString baos "utf-8")]
       (.reset baos)
       (.encodeToString (Base64/getEncoder) (.getBytes ret "utf-8")))
     :cljs
     (js/btoa (tx/write-str (tx/write-meta (preprocess data))))))

(defn decode [s]
  #?(:clj
     (let [decoded (String. (.decode (Base64/getDecoder) s) "utf-8")
           bais    (ByteArrayInputStream. (.getBytes decoded "utf-8"))
           reader  (tx/reader bais :json)]
       (tx/read reader))
     :cljs
     (tx/read-str (js/atob s))))

#?(:clj (defn keywordize [x]
          (walk/postwalk
           #(cond-> % (string? %) keyword)
           x)))

#?(:clj (defn read-dispatch [{:keys [query-string]}]
          (def query-string query-string)
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
