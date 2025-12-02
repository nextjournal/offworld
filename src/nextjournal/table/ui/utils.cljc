(ns nextjournal.table.ui.utils)

(def conjv (fnil conj []))

(defn append-path-prefix [state state-old path]
  (assoc state :state/path-prefix (vec (concat (:state/path-prefix state-old) path))))

(defn substate [state path]
  (-> state
      (get-in path)
      (append-path-prefix state path)))
