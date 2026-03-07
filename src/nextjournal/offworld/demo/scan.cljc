(ns nextjournal.offworld.demo.scan
  (:require
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as 🪐]
   [nexus.registry :as nxr]))

(defn rand-plate []
  (let [letters (mapv char (range 65 91))
        rand-letters (fn [n] (apply str (repeatedly n #(rand-nth letters))))
        rand-digits (fn [n] (apply str (repeatedly n #(rand-int 10))))]
    (str (rand-nth ["NL" "DE" "FR" "BE" "ES" "IT" "PL" "CZ" "DK" "SE"])
         "-" (rand-letters 2)
         "-" (rand-digits 4))))

(defn init-state [state]
  (assoc state ::plates (take 3 (repeatedly rand-plate))))

(nxr/register-action! ::scan ^::🪐/client
  (fn [_ plate]
    [[:effects/save [::scans plate] :scanned]]))

(k/defq get-plates [stem] (::plates stem))

(k/defq get-scan [stem plate] (some-> stem (get ::scans) (get plate)))

(defn wrap-interest [{:keys [id label]} & children]
  (let [id          id
        pop-id      (str id "-pop")
        anchor-name (str "--" id)]
    [:button {:id          id
              :interestfor pop-id
              :style       {:anchor-name anchor-name}}
     children
     [:div.bg-amber-200.rounded-md.text-xs
      {:id      pop-id
       :popover :auto
       :style   {:position        :fixed
                 :position-anchor anchor-name
                 :position-area   "bottom center"}}
      label]]))

(defn truck [{:keys [plate] ::k/keys [stem]}]
  [:button.border.cursor-pointer
   {:on {:click [^::🪐/replicate [::scan plate]]}}
   [:div {:style {:font-size 60}} "🚚"]
     [:div plate]
   [:label "Client: " (case (get-scan stem plate) nil "⛶" :scanned "✅")]
   [:label "Server: " (case (get-scan stem plate) nil "⛶" :scanned "✅")]])

(defn game [{::k/keys [path stem] :as state}]
  [:div.flex.items-start.gap-2
   (map #(wrap-interest
          {:id    %
           :label "Click to \"scan\""}
          (truck (k/+ state (conj path %) {:plate %})))
        (get-plates stem))])
