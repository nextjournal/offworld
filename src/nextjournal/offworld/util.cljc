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

#?(:squint
   (defclass WireStr
     (extends js/String)
     (constructor [this s] (super s))))

;; non-iterable, else seq?/walk shred the mark into characters
#?(:squint
   (js/Object.defineProperty (.-prototype WireStr) js/Symbol.iterator
                             #js {:value js/undefined}))

(defn str!
  "Marks a string that must stay a string on the wire. Every unmarked
  string in the dispatch payload encodes as a keyword."
  [s]
  #?(:squint (WireStr. s)
     :default s))

(defn- kw-walk [x]
  (cond
    #?@(:squint [(instance? WireStr x) (str x)])
    (string? x) (wire-kw x)
    (map? x) (update-vals x kw-walk)
    (vector? x) (mapv kw-walk x)
    (set? x) (into #{} (map kw-walk) x)
    :else x))

(defn unmark
  "Recursively unwraps str! marks: client-executed effects must see plain
  strings, JS libraries reject boxed String objects."
  [x]
  #?(:squint (cond
               (instance? WireStr x) (str x)
               (map? x) (update-vals x unmark)
               (vector? x) (mapv unmark x)
               (set? x) (into #{} (map unmark) x)
               :else x)
     :default x))

(defn preprocess
  "Restores keyword types on the client dispatch payload before transit
  encoding: marked strings (decoded wire strings and str! values) stay
  strings, all other strings become keywords."
  [payload]
  (kw-walk payload))

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
     ;; wire strings decode marked, so a decode/encode round trip keeps
     ;; strings and keywords apart even though both are strings at runtime
     :squint
     (tx/read-str (js/atob s) {:decode-string #(WireStr. %)})
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
