(ns nextjournal.table.ui.omnibox)

(defn input [{:keys [rows choices filters set-filters text !expanded !selected keyword? numeric? date? data-testid] :as opts}]
  [:input
   {:type "text"
    :class ["w-full" "cursor-default" "rounded-[3px]" "px-[6px]"
            "ring-1" "ring-slate-300" "font-normal" "placeholder-slate-400"
            "focus:outline-none" "focus:ring-2" "focus:ring-blue-500" "sm:leading-6"
            "bg-white"]
    :placeholder "Filter..."
    :data-testid data-testid
    :value       text
    :on {:input [[:effects/save [::value] [:event.target/value]]
                 #_(do
                     (reset! !text (-> % .-currentTarget .-value))
                     (reset! !selected nil)
                     (compare-and-set! !expanded false true))]
         :key-down [#_(fn [e]
                        (case (.-key e)
                          "Enter"
                          (if-some [selected @!selected]
                            (on-row-click e (nth rows selected) opts)
                            (when-some [filter-from-text (or ; don't put text->filter in cond :else, always fall back to text filter when ->numeric returns nil (eg for filtering numeric by "2*")
                                                          (cond
                                                            (:text->filter opts) ((:text->filter opts) @!text)
                                                            numeric? (text->numeric-filter @!text)
                                                            date? (text->date-filter @!text)
                                                            keyword? (text->filter (name @!text)))
                                                          (text->filter @!text))]
                              (.preventDefault e)
                              (set-filters (conj filters filter-from-text))
                              (reset! !text "")
                              (reset! !expanded false)))

                          "Escape"
                          (do
                            (.preventDefault e)
                            (reset! !selected nil)
                            (reset! !text "")
                            (reset! !expanded false))

                          " "
                          (when-some [selected @!selected]
                            (on-row-click e (nth rows selected) opts))

                          "ArrowDown"
                          (do
                            (.preventDefault e)
                            (update-selected opts 1))

                          "ArrowUp"
                          (do
                            (.preventDefault e)
                            (update-selected opts -1))

                          nil))]}}])


(defn popover [opts]
  [:div "popover"])

(defn omnibox [opts]
  [:div
   (input opts)
   (when (:popover-visible opts)
     (popover opts))
   (pr-str opts)])
