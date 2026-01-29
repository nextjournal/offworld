(ns nextjournal.table.util
  (:require
   [clojure.set :as set]
   [clojure.walk :as walk]
   [clojure.edn :as edn]
   [clojure.core.async :as a]
   [nextjournal.table.ui.utils :as ui.utils]
   [nextjournal.ductile.load-builder :as load-builder]
   #?(:clj [clojure.java.io :as io])
   #?(:clj [ring.core.protocols :refer [StreamableResponseBody]]))
  #?(:clj
     (:import
      (clojure.core.async.impl.channels ManyToManyChannel))))

#?(:clj
   (defn channel->output-stream [channel output-stream]
     (with-open [out    output-stream
                 writer (io/writer out)]
       (loop []
         (when-let [^String msg (a/<!! channel)]
           (doto writer (.write msg) (.flush))
           (recur))))))

#?(:clj
   (extend-type ManyToManyChannel
     StreamableResponseBody
     (write-body-to-stream [ch _response output-stream]
       (channel->output-stream ch output-stream))))

#?(:clj
   (defn sse-handler [sse-chan]
     (fn [_request]
       (println "Connected to SSE endpoint")
       {:status  200,
        :headers {"Content-Type"  "text/event-stream"
                  "Cache-Control" "no-cache, no-store"},
        :body    sse-chan})))

(defn init-store
  []
  {:grid {:row-tree    (into [:root]
                             (map (fn [a]
                                    (into []
                                          (map (fn [b]
                                                 {:id   (keyword (str "r" a b))
                                                  :size (rand-nth [20 25 30 35
                                                                   40])}))
                                          (range 10))))
                             (range 50))
          :column-tree (into [:root]
                             (map (fn [a]
                                    (into []
                                          (map (fn [b]
                                                 {:id   (keyword (str "c" a b))
                                                  :size (rand-nth [20 25 30 35
                                                                   40])}))
                                          (range 10))))
                             (range 50))
          :size-cache  (volatile! {})}
   :omnibox
   {[:transport/destination :address/city]
    {:id          [:transport/destination :address/city]
     :tick        1
     :choices     (get-in load-builder/filters [[:transport/destination :address/city] 1])
     :filters     #{}
     :set-filters (fn [])}
    [:transport/destination :address/postcode]
    {:id          [:transport/destination :address/postcode]
     :tick        1
     :choices     (get-in load-builder/filters [[:transport/destination :address/postcode] 1])
     :filters     #{}
     :set-filters (fn [])}}})
