(ns nextjournal.transit-lite)

;; Minimal transit JSON codec in pure CLJS — no transit-js dependency.
;; Supported types: nil, boolean, number, string, keyword, symbol, uuid,
;;                  VectorLite, ObjMap, HashMapLite, SetLite, WithMeta.

(deftype WithMeta [value meta])

;; ---------------------------------------------------------------------------
;; Writer

(declare encode)

(defn- encode-str [s]
  (let [c (.charAt s 0)]
    (if (or (= "~" c) (= "^" c)) (str "~" s) s)))

(defn- encode-key [k]
  (cond
    (keyword? k) (str "~:" (.-fqn k))
    (string? k)  (encode-str k)
    (symbol? k)  (str "~$" (.-str k))
    :else (throw (js/Error. "transit-lite: unsupported map key"))))

(defn encode [x]
  (cond
    (nil? x)               nil
    (boolean? x)           x
    (number? x)            x
    (string? x)            (encode-str x)
    (keyword? x)           (str "~:" (.-fqn x))
    (symbol? x)            (str "~$" (.-str x))
    (instance? UUID x)     (str "~u" (.-uuid x))
    (vector? x)            (let [a #js []]
                             (run! #(.push a (encode %)) x)
                             a)
    (map? x)               (let [a #js ["^ "]]
                             (reduce-kv (fn [_ k v]
                                          (.push a (encode-key k) (encode v)))
                                        nil x)
                             a)
    (set? x)               (let [a #js []]
                             (run! #(.push a (encode %)) x)
                             #js {"~#set" a})
    (instance? WithMeta x) #js {"~#with-meta"
                                #js [(encode (.-value x)) (encode (.-meta x))]}
    :else (throw (js/Error. "transit-lite: unsupported type"))))

(defn write-str [x] (js/JSON.stringify (encode x)))

(defn write-meta [x]
  (if (implements? IMeta x)
    (let [m (-meta ^not-native x)]
      (if (nil? m) x (WithMeta. (-with-meta ^not-native x nil) m)))
    x))

;; ---------------------------------------------------------------------------
;; Reader
;;
;; Transit uses a circular string cache to compress repeated tagged values.
;; Cache refs are "^X" where X is a char at ASCII offset 33 from the index.

(def ^:private CACHE-SIZE 88)
(def ^:private BASE-CHAR  33)

(defn- new-cache [] #js {:d (js/Array. CACHE-SIZE) :n 0})

(defn- cache-ref? [s]
  (and (= 2 (.-length s)) (= "^" (.charAt s 0)) (not= " " (.charAt s 1))))

(defn- cache-get [c s]
  (aget (.-d c) (- (.charCodeAt s 1) BASE-CHAR)))

(defn- cache-put! [c v]
  (aset (.-d c) (mod (.-n c) CACHE-SIZE) v)
  (set! (.-n c) (+ (.-n c) 1))
  v)

(declare decode)

(defn- decode-str [s cache as-map-key?]
  (if (cache-ref? s)
    (cache-get cache s)
    (let [decoded (if (= "~" (.charAt s 0))
                    (case (.charAt s 1)
                      ":" (keyword (.substring s 2))
                      "$" (symbol  (.substring s 2))
                      "u" (uuid    (.substring s 2))
                      "~" (.substring s 1)
                      "^" (.substring s 1)
                      s)
                    s)]
      (when (and (> (.-length s) 2)
                 (or as-map-key? (not= "~" (.charAt s 1))))
        (cache-put! cache decoded))
      decoded)))

(defn- decode-array [a cache]
  (if (= "^ " (aget a 0))
    ;; transit map
    (loop [i 1 ret (transient {})]
      (if (< i (.-length a))
        (recur (+ i 2)
               (-assoc! ret
                       (decode-str (aget a i) cache true)
                       (decode (aget a (+ i 1)) cache)))
        (persistent! ret)))
    ;; vector
    (loop [i 0 ret (transient [])]
      (if (< i (.-length a))
        (recur (+ i 1) (conj! ret (decode (aget a i) cache)))
        (persistent! ret)))))

(defn- decode-tagged [obj cache]
  (let [tag (aget (js/Object.keys obj) 0)
        val (aget obj tag)]
    (case tag
      "~#set"       (reduce conj #{} (decode val cache))
      "~#list"      (into () (.reverse (decode val cache)))
      "~#with-meta" (with-meta (decode (aget val 0) cache)
                               (decode (aget val 1) cache))
      "~#cmap"      (loop [i 0 ret (transient {})]
                      (if (< i (.-length val))
                        (recur (+ i 2)
                               (-assoc! ret
                                       (decode (aget val i) cache)
                                       (decode (aget val (+ i 1)) cache)))
                        (persistent! ret)))
      (throw (js/Error. (str "transit-lite: unknown tag " tag))))))

(defn decode [x cache]
  (cond
    (nil? x)    nil
    (boolean? x) x
    (number? x) x
    (string? x) (decode-str x cache false)
    (array? x)  (decode-array x cache)
    :else       (decode-tagged x cache)))

(defn read-str [s] (decode (js/JSON.parse s) (new-cache)))
