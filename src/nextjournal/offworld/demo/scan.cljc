(ns nextjournal.offworld.demo.scan
  (:require
   [nextjournal.baseline :as k]
   [nextjournal.offworld :as ow :refer [defc]]
   [nextjournal.offworld.util :as ou]
   [nexus.registry :as nxr]))

(defn rand-plate []
  (let [letters      (mapv char (range 65 91))
        rand-letters (fn [n] (apply str (repeatedly n #(rand-nth letters))))
        rand-digits  (fn [n] (apply str (repeatedly n #(rand-int 10))))]
    (str (rand-nth ["NL" "DE" "FR" "BE" "ES" "IT" "PL" "CZ" "DK" "SE"])
         "-" (rand-letters 2)
         "-" (rand-digits 4))))

(defn init-state [state]
  (assoc state ::plates (take 9 (repeatedly rand-plate))))

(nxr/register-action! ::scan ^::ow/client
  (fn [_ plate]
    [[:effects/save [::scans plate] :scanned]]))

(nxr/register-action! ::cancel ^::ow/client
  (fn [_ plate]
    [[:effects/save [::scans plate] :canceled]]))

(k/defq get-plates
  {::k/paths #{[::plates]}}
  [stem]
  (::plates stem))

(k/defq get-scans
  {::k/paths #{[::scans]}}
  [stem]
  (::scans stem))

(k/defq get-scan
  {::k/deps #{`get-scans}}
  [stem plate]
  (some-> stem get-scans (get plate)))

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

(defn status-icon [scan]
  (case scan
    nil      [:span.invisible "✅"]
    :scanned "✅"
    :canceled "❌"))

(defn truck [{:keys [plate] ::k/keys [stem]}]
  (let [{::ow/keys [offline? last-server-stem]}
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
   (map #(wrap-interest
          {:id    %
           :label "Click to \"scan\""}
          (truck (k/+ state (conj path %) {:plate %})))
        (get-plates stem))])

(defc offline-game [& args]
  [:dialog {:style              {:position  "fixed"
                                 :top       "50%"
                                 :left      "50%"
                                 :transform "translate(-50%, -50%)"}
            :replicant/on-mount [[:node/show-modal]]}
   "OFFLINE MODE:"
   (apply game args)])

(comment
  (let [offline-state
        (-> (js/document.getElementById "scan-game-offline")
            (.getAttribute "data-offworld-sync")
            ou/deserialize)
        {:keys    [queries render-fn local id]
         ::k/keys [path stem config]} offline-state
        render-fn                     (get @ow/render-fn-registry render-fn)]
    (def offline-state offline-state)
    (def stem stem) (def path path) (def config config)
    (render-fn (k/+ {::k/stem stem} path config))))
