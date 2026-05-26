(ns nextjournal.offworld.demo.scan
  (:require
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as 🪐 :refer [defc]]
   [nexus.registry :as nxr]))

(defn rand-plate []
  (rand-nth ["NL-AS-1829" "DE-BC-3829" "FR-FG-3173" "BE-DX-3882" "ES-ZX-5934" "IT-AG-3847"
             "PL-GS-2210" "CZ0-AZ-3939" "DK-39-1119" "SE-30-1199"]))

(defn init-state [state]
  (assoc state ::plates (take 9 (repeatedly rand-plate))))

(nxr/register-action! ::scan ^::🪐/client
  (fn [_ plate]
    [[:effects/save [::scans plate] :scanned]]))

(nxr/register-action! ::cancel ^::🪐/client
  (fn [_ plate]
    [[:effects/save [::scans plate] :canceled]]))

(defn get-plates
  {::k/paths #{[::plates]}}
  [stem]
  (::plates stem))

(defn get-scans
  {::k/paths #{[::scans]}}
  [stem]
  (::scans stem))

(defn get-scan
  {::k/deps #{`get-scans}}
  [stem plate]
  (some-> stem get-scans (get plate)))

(defn wrap-interest [{:keys [id label]} & children]
    (let [id          id
          pop-id      [id "-pop"]
          anchor-name ["--" id]]
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

(defn status-icon [scan]
  (case scan
    nil      [:span.invisible "✅"]
    :scanned "✅"
    :canceled "❌"))

(defn truck [{:keys [plate] ::k/keys [stem]}]
  (let [{::🪐/keys [offline? last-server-stem]}
        stem
        scan        (get-scan stem plate)
        server-scan (if offline? (get-scan last-server-stem plate) scan)]
    [:button.cursor-pointer.pr-4
     {:on {:click (case scan
                    (nil :canceled) [[::scan plate]]
                    :scanned        [[::cancel plate]])}}
     [:div {:style {:font-size 60}} "🚚"]
     [:div [:code.bg-slate-200.border.border-slate-500 plate]]
     [:label "Client: " (status-icon scan)]
     [:label "Server: " (status-icon server-scan)]]))

(defc game [{::k/keys [path stem] :as state}]
  [:div.flex.flex-wrap.items-start.max-w-160
   (let [x (first (get-plates stem))]
     (wrap-interest
      {:id    x
       :label "Click to \"scan\""}
      (truck (k/+ state (conj path x) {:plate x}))))])

(defc offline-game [& args]
  [:dialog {:style              {:position  "fixed"
                                 :top       "50%"
                                 :left      "50%"
                                 :transform "translate(-50%, -50%)"}
            :replicant/on-mount [[:node/show-modal]]}
   "OFFLINE MODE:"
   (apply game args)])

(game {})
(offline-game {})
