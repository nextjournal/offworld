(ns nextjournal.offworld-test
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing use-fixtures]]
      :cljs [cljs.test :refer-macros [deftest is testing use-fixtures]])
   [nexus.registry :as nxr]
   [nextjournal.offworld :as-alias 🪐]))

(def system (atom {}))
(def server ::🪐/server)
(def client ::🪐/client)
(def placeholder :nexus/placeholders)
(def expansion :nexus/expansions)
(def effect :nexus/effects)

(defn split-by [pred coll]
  (reduce (fn [[yes no] x]
            (if (pred x)
              [(conj yes x) no]
              [yes (conj no x)]))
          [[] []]
          coll))

(defn infer-kind [nexus [action-k]]
  (some #(when (get-in nexus [% action-k]) %)
        [placeholder expansion effect]))

(defn infer-world
  ([nexus action] (infer-world nexus (infer-kind nexus action) action))
  ([nexus kind [action-k]]
   (let [handler (get-in nexus [kind action-k])]
     (if (server (meta handler)) server client))))

(defn ix [{:keys [nexus actions] :as ctx}]
  (let [server?          #(= server (infer-world nexus %))
        [server-actions
         client-actions] (split-by server? actions)]
    (cond-> ctx
      (seq server-actions)
      (-> (assoc :actions client-actions)
          (update ::🪐/server-actions
                  (fnil into [])
                  (filterv server? actions))))))

(defn- blank-registry-fixture
  [f]
  (let [old-registry @nxr/!registry]
    (reset! nxr/!registry {})
    (nxr/register-system->state! deref)
    (reset! system {})
    (nxr/register-interceptor! :before-action ix)
    (try
      (f)
      (finally
        (reset! nxr/!registry old-registry)))))

(use-fixtures :each blank-registry-fixture)

(deftest test-divert-server-action
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _]))
  (let [{::🪐/keys [server-actions]} (nxr/dispatch system {} [[::server-fx]])]
    (is (= [[::server-fx]] server-actions))))

(deftest test-divert-l2-server-action
  (nxr/register-effect! ::server-fx ^::🪐/server (fn [& _]))
  (nxr/register-expansion! ::client-xp (fn [& _] [[::server-fx]]))
  (let [{::🪐/keys [server-actions]} (nxr/dispatch system {} [[::client-xp]])]
    (is (= [[::server-fx]] server-actions))))
