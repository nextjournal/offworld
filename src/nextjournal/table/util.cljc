(ns nextjournal.table.util
  (:require
   [clojure.walk :as walk]
   [clojure.core.async :as a]
   [nextjournal.table.ui.utils :as ui.utils]
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

(defn d*-dispatch [actions]
  (str "@get('/replicant-dispatch', {payload: {actions: '"
       (pr-str actions)
       "', "
       "dispatch_data: {value: evt.target.value, "
       "scroll_top: evt.target.scrollTop, "
       "scroll_left: evt.target.scrollLeft}}})"))

(defn on-hooks-replicant->d*
  "Converts a map containing replicant-style :on attributes to
  a map containing datastar expressions. E.g.:

  {:on {:click [[:my-action]]}}
  {:data-on:click \"@get('/replicant-dispatch', {payload: '[[:my-action]]'})\"}"
  [props]
  (into (dissoc props :on)
        (for [[k v] (:on props)
              :let  [{:datastar/keys [modifiers]} (meta v)]]
          [(keyword (apply str "data-on" k (interleave (repeat "__")
                                                       (map name modifiers))))
           (d*-dispatch v)])))

(defn replicant->d* [hiccup]
  (walk/postwalk
   #(cond-> % (map? %) on-hooks-replicant->d*)
   hiccup))

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
          :size-cache  (volatile! {})}})
