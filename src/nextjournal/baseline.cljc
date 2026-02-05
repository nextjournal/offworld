(ns nextjournal.baseline
  (:refer-clojure :exclude [+])
  (:require
   [clojure.string :as str]
   [nextjournal.baseline :as-alias k]
   [nexus.registry :as nxr]))

(def conjv (fnil conj []))

(def registry (atom {}))

(defn register! [k f]
  (swap! registry
         #(let [{::k/keys [deps]} (meta f)]
            (cond-> %
              :do  (update ::fns assoc k f)
              deps (update ::k/deps assoc k deps)))))

(defn with-deps [state]
  (assoc state ::k/deps (::k/deps @registry)))

(defn + [{::k/keys [domain path local] :or {path [::local]}} path-suffix & [config]]
  (let [path-suffix (if (sequential? path-suffix) path-suffix [path-suffix])]
    (cond-> (or config {})
      domain (assoc ::k/domain domain)
      :do    with-deps
      :do    (assoc ::k/path  (vec (concat path path-suffix)))
      :do    (assoc ::k/local (get-in local path-suffix)))))

(defn explain-trace [{:keys [stack]}]
  (->> stack
       (map-indexed
        (fn [i k]
          (str (apply str (repeat (* 2 i) " "))
               (if (zero? i) "" "└─ ")
               k)))
       (str/join "\n")))

(defn q [m k & opts]
  (let [{:as      domain
         ::k/keys [trace]} (get m ::domain m)
        f                  (get-in @registry [::fns k])
        parent             (some-> trace deref :stack peek)]
    (when trace
      (swap! trace
             #(cond-> %
                parent
                (update :queries conj [parent k])
                (nil? parent)
                (update :roots conj k)
                :do
                (update :stack conj k))))
    (when-not f ;; TODO: make this work for calls to `k/q`, not only calls to `k/trace`.
      (throw
       (ex-info
        (str "Missing query: " k "\n\n"
             (when trace (explain-trace @trace)))
        {:k     k
         :trace (when trace @trace)})))
    (try
      (apply f domain opts)
      (finally
        (when trace
          (swap! trace update :stack pop))))))

(defn trace [m k & opts]
  (let [domain  (get m ::domain m)
        trace*  (atom {:stack [] :queries [] :roots #{}})
        domain' (assoc domain ::k/trace trace*)
        value   (apply q domain' k opts)]
    {:value value
     :deps  (dissoc @trace* :stack)}))

(nxr/register-action! ::save
 ^:nexus/batch
 (fn [_ store path-vs]
   (swap! store
          (fn [state]
            (reduce (fn [acc [path v]]
                      (assoc-in acc (into [::domain] path) v))
                    state path-vs)))))

(comment
  (-> {::k/local {:x {:a 1 :b {:c :d :e :f}}}
       ::k/domain {:areas {:cars :trucks}}}
      (+ :x)
      (+ :b)))
