(ns nextjournal.offworld.order
  "Action ordering policies for offworld SSE connections.

  Policies are attached to actions as metadata (::🪐/order). The client
  stamps actions via propose!/propose; the server checks them via check,
  returning {:fx [...] :state new-state}.

  fx entries:  [:dispatch actions] — forward to nexus
               [:drop]            — discard silently
               [:timeout opts]    — schedule a dispatch after :ms ms

  Built-in policies: :seq-gate, :bounded-buffer."
  (:require
   [nextjournal.offworld :as-alias 🪐]))

#?(:cljs (def proposal-system (atom {})))

(def ^:private conjv (fnil conj []))

(defn- contiguous? [xs] (every? #{1 -1} (map - (rest xs) xs)))

(defn- ->v [x] (if (sequential? x) (vec x) [x]))

(def ^:private get-order (comp ->v ::🪐/order meta))

(def ^:private get-policy (comp first get-order))

(def ^:private get-args (comp rest get-order))

(defmulti check*
  "Policy implementation for check. Receives policy-scoped state (not full
  actor-state). Returns {:fx [...] :state new-policy-state}."
  (fn [_ actions] (get-policy actions)))

(defn check
  "Apply the ordering policy tagged on actions to actor-state.
  Returns {:fx [...] :state new-actor-state}."
  [actor-state actions]
  (let [policy                     (get-policy actions)
        state                      (get actor-state policy)
        {:as res new-state :state} (check* state actions)
        new-actor-state            (assoc actor-state policy new-state)]
    (assoc res :state new-actor-state)))

(defmulti propose
  "Stamp actions with sequence metadata for the given policy. Called on the
  client before sending the actions to the server. Returns {:args [...] :state new-policy-state}."
  (fn [_ actions] (get-policy actions)))

#?(:cljs
   (defn propose!
     ([actions] (propose! proposal-system actions))
     ([system actions]
      (let [policy                            (get-policy actions)
            {:keys [args state] :as proposal} (propose (get @system policy) actions)]
        (when (contains? proposal :state)
          (swap! system assoc policy state))
        (cond-> actions
          (contains? proposal :args) (vary-meta assoc ::🪐/order (into [policy] args)))))))

(defmethod propose :default [_ _])

(defmethod check* :default [state actions]
  {:fx    [[:dispatch actions]]
   :state state})

(defmethod propose :seq-gate [state actions]
  (let [[& {k :key}] (get-args actions)
        next-num   (inc (get state k -1))]
    {:args  [:key k :seq-num next-num]
     :state (assoc state k next-num)}))

(defmethod check* :seq-gate [state actions]
  (let [[& {k :key sn :seq-num}] (get-args actions)
        next-num                 (inc (get state k -1))]
    (if (= sn next-num)
      {:fx    [[:dispatch actions]]
       :state (assoc state k next-num)}
      {:fx    [[:drop]]
       :state state})))

(defmethod propose :bounded-buffer [state actions]
  (let [[& {:as opts k :key}] (get-args actions)
        next-num              (inc (get state k -1))]
    {:args  [(assoc opts :seq-num next-num)]
     :state (assoc state k next-num)}))

(def ^:private seq-num
  "Extract sequence number from a tagged actions vector."
  (comp :seq-num first get-args))

(defmethod check* :bounded-buffer [state actions]
  (let [[&
         {k     :key
          :keys [size-bound
                 time-bound]
          :or   {size-bound 3
                 time-bound 500}}] (get-args actions)
        {sn  :seq-num
         buf :buf
         :or {sn -1}}              (get state k)
        new-buf                    (sort-by seq-num (conjv buf actions))
        status                     (cond
                                     (contiguous?
                                      (conj (map seq-num new-buf) sn)) :contiguous
                                     (<= (seq-num actions) sn)         :stale
                                     (> (count new-buf) size-bound)    :overflow
                                     :else                             :gap)]
    {:policy :bounded-buffer
     :status status
     :state  (->> (case status
                    :stale        {:buf     buf
                                   :seq-num sn}
                    :gap          {:buf     new-buf
                                   :seq-num sn}
                    (:overflow
                     :contiguous) {:buf     nil
                                   :seq-num (->> new-buf
                                                 (map seq-num)
                                                 (apply max))})
                  (assoc state k))
     :fx     (case status
               :gap          [[:timeout {:policy :bounded-buffer
                                         :key    k
                                         :ms     time-bound}]]
               :stale        [[:drop]]
               (:overflow
                :contiguous) [[:dispatch (apply concat new-buf)]])}))

(defn handle-timeout
  "Called when a scheduled timeout fires. Re-reads the current buffer from
  actor-state and flushes it if non-empty. Returns {:fx [...] :state new-actor-state},
  or {:state actor-state} if the buffer was already flushed."
  [actor-state {k :key :keys [policy]}]
  (let [buf (get-in actor-state [policy k :buf])]
    (if-not (seq buf)
      {:state actor-state}
      {:state (assoc-in actor-state
                        [policy k] {:buf     nil
                                    :seq-num (seq-num (last buf))})
       :fx    [[:dispatch (apply concat buf)]]})))
