(ns nextjournal.offworld.demo.ui.resizer
  (:require
   [clojure.string :as str]
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]
   [nextjournal.offworld.demo.ui.resizer :as-alias rz]
   [nextjournal.offworld.guard :as-alias 🚦]
   [nextjournal.offworld.stem :as 🌿]))

(def cols [:name :city :role])

(def rows
  [{:name "Ada"       :city "Berlin"  :role "Engineer"}
   {:name "Grace"     :city "Warsaw"  :role "Analyst"}
   {:name "Katherine" :city "Krakow"  :role "Engineer"}
   {:name "Radia"     :city "Gdansk"  :role "Manager"}])

(def default-width 120)

(defn- widths-of [grid-el]
  #?(:cljs (str/split (.. (js/getComputedStyle grid-el) -gridTemplateColumns) #" ")))

(nxr/register-placeholder! ::rz/dragging?
  (fn [dispatch-data]
    #?(:cljs (let [node (:replicant/node dispatch-data)
                   ev   (:replicant/dom-event dispatch-data)]
               (boolean (and node ev (.hasPointerCapture node (.-pointerId ev))))))))

(nxr/register-placeholder! ::rz/width
  (fn [_ grid-id idx]
    #?(:cljs (js/parseInt (nth (widths-of (js/document.getElementById grid-id)) idx)))))

(nxr/register-effect! ::rz/grab ^::🪐/client
  (fn [ctx _system]
    #?(:cljs (let [{:replicant/keys [node dom-event]} (:dispatch-data ctx)]
               (.setPointerCapture node (.-pointerId dom-event))))))

(nxr/register-effect! ::rz/preview ^::🪐/client
  (fn [ctx _system grid-id idx]
    #?(:cljs
       (let [dx      (.-movementX (:replicant/dom-event (:dispatch-data ctx)))
             grid-el (js/document.getElementById grid-id)
             ws      (vec (widths-of grid-el))
             next-w  (max 40 (+ (js/parseInt (nth ws idx)) dx))]
         (set! (.. grid-el -style -gridTemplateColumns)
               (str/join " " (assoc ws idx (str next-w "px"))))))))

(nxr/register-effect! ::rz/commit ^::🪐/server
  (fn [_ system path c width]
    (swap! system assoc-in (concat path [c]) width)))

(defn- handle [{:keys [grid-id idx c path]}]
  [:div {:style {:position "absolute" :top 0 :right 0 :height "100%"
                 :width "10px" :transform "translateX(50%)"
                 :cursor "col-resize" :background "var(--success-soft)"
                 :opacity 0.5}
         :on {:pointerdown [[::rz/grab]]
              :pointermove [[::🚦/guard [::rz/dragging?]
                             [[::rz/preview grid-id idx]]]]
              :pointerup   [[::🚦/guard [::rz/dragging?]
                             [[::rz/commit path c [::rz/width grid-id idx]]]]]}}])

(defn grid [{:as state :keys [cols rows]}]
  (let [saved   (or (🌿/local state) {})
        grid-id (🌿/id state)
        path    (🌿/path state)]
    [:div {:id grid-id
           :style {:display :grid
                   :grid-template-columns
                   (str/join " " (map #(str (get saved % default-width) "px") cols))}}
     (map-indexed
      (fn [idx c]
        [:div {:id (🌿/id (🌿/+ state [:header c]))
               :style {:position "relative" :font-weight "bold" :font-size "0.85em"
                       :padding-left "0.5rem"
                       :border-bottom "1px solid var(--border-mid)"}}
         (name c)
         (when-not (= c (last cols))
           (handle {:grid-id grid-id :idx idx :c c :path path}))])
      cols)
     (for [ri (range (count rows)) c cols]
       [:div {:id (🌿/id (🌿/+ state [:row ri c]))
              :style {:color "var(--fg-faint)" :font-size "0.85em"
                      :padding-left "0.5rem"}}
        (get (nth rows ri) c)])]))

(defn panel [state]
  [:div
   [:h2 {:style {:font-weight "bold"}} "Resizer — per-frame preview, one commit"]
   (grid (🌿/+ state [::rz/widths] {:cols cols :rows rows}))])
