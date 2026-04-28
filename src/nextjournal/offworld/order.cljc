(ns nextjournal.offworld.order
  (:require
   [nextjournal.offworld :as-alias 🪐]))

#?(:cljs (def proposal-system (atom {})))

(def ^:private conjv (fnil conj []))

(defn- contiguous? [xs] (every? #{1 -1} (map - (rest xs) xs)))

(defn- ->v [x] (if (sequential? x) (vec x) [x]))

(defn- get-policy [actions]
  (first (::🪐/order (meta actions))))

(defn- get-opts [actions]
  (second (::🪐/order (meta actions))))

(defmulti check
  "Server-side gate. Given the current ordering state and an incoming tagged
  actions vector, returns {:status :state :dispatches} and optionally :timeout.
  The host must schedule a `timeout` call if :timeout is present."
  (fn [_ actions] (get-policy actions)))

(defmulti propose
  "Client-side stamp. Given the current ordering state and an actions vector,
  returns updated opts and state to tag the actions before they are sent."
  (fn [_ actions] (get-policy actions)))

(defn propose!
  ([actions] (propose! proposal-system actions))
  ([system actions]
   (let [policy                            (get-policy actions)
         {:keys [opts state] :as proposal} (propose (get @system policy) actions)]
     (when (contains? proposal :state)
       (swap! system assoc policy state))
     (cond-> actions
       (contains? proposal :opts) (vary-meta assoc ::🪐/order [policy opts])))))

(defmethod propose :default [_ _])

(defmethod check :default [state actions] {:status     :allow
                                           :state      state
                                           :dispatches [actions]})

(defmethod propose :seq-gate [state actions]
  (let [[k]      (->v (get-opts actions))
        next-num (inc (get state k -1))]
    {:opts  [k next-num]
     :state (assoc state k next-num)}))

(defmethod check :seq-gate [state actions]
  (let [[k n]    (get-opts actions)
        next-num (inc (get state k -1))]
    (if (= n next-num)
      {:status     :allow
       :state      (assoc state k next-num)
       :dispatches [actions]}
      {:status :block :state state})))

(defmethod propose :bounded-buffer [state actions]
  (let [[k]      (->v (get-opts actions))
        next-num (inc (get state k -1))]
    {:opts  [k next-num]
     :state (assoc state k next-num)}))

(def ^:private seq-num
  "Extract sequence number from a tagged actions vector."
  (comp second get-opts))

(defmethod check :bounded-buffer [state actions]
  (let [bound                      3
        [k n & {:keys [overflow]}] (get-opts actions)
        {:keys [buf last-sn]
         :or   {last-sn -1}}       (get state k)
        new-buf                    (sort-by seq-num (conjv buf actions))
        [reason status]            (cond
                                     (contiguous?
                                      (sort
                                       (conj (map seq-num new-buf)
                                             last-sn)))             [:contiguous :flush]
                                     (<= (seq-num actions) last-sn) [:stale :ignore]
                                     (> (count new-buf) bound)      [:overflow :flush]
                                     :else                          [:gap :enqueue])
        new-state                  (case status
                                     :flush   {:last-sn (apply max (map seq-num new-buf)) :buf nil}
                                     :ignore  state
                                     :enqueue {:last-sn last-sn
                                               :buf     new-buf})]
    {:policy     :buffer
     :status     status
     :reason     reason
     :dispatches (case status :flush (sort-by seq-num new-buf) nil)
     :state      (assoc state k new-state)}))
