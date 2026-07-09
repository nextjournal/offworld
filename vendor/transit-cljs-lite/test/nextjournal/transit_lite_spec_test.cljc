(ns nextjournal.transit-lite-spec-test
  (:require [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]
            [#?(:clj clojure.spec.alpha :cljs cljs.spec.alpha) :as s]
            [nextjournal.transit-lite-spec :as tls]))

(deftest valid-scalars
  (is (s/valid? ::tls/value nil))
  (is (s/valid? ::tls/value true))
  (is (s/valid? ::tls/value false))
  (is (s/valid? ::tls/value 42))
  (is (s/valid? ::tls/value 3.14))
  (is (s/valid? ::tls/value "hello"))
  (is (s/valid? ::tls/value :foo))
  (is (s/valid? ::tls/value :ns/foo))
  (is (s/valid? ::tls/value 'bar))
  (is (s/valid? ::tls/value #uuid "00000000-0000-0000-0000-000000000000")))

(deftest valid-collections
  (is (s/valid? ::tls/value []))
  (is (s/valid? ::tls/value [1 "two" :three]))
  (is (s/valid? ::tls/value {}))
  (is (s/valid? ::tls/value {:a 1 "b" 2 'c 3}))
  (is (s/valid? ::tls/value #{}))
  (is (s/valid? ::tls/value #{1 :two "three"})))

(deftest valid-nested
  (is (s/valid? ::tls/value {:items [1 2 3] :tags #{:a :b} :name "foo"}))
  (is (s/valid? ::tls/value [{:id 1} {:id 2}])))

(deftest valid-metadata
  (is (s/valid? ::tls/value (with-meta {:a 1} {:source :cache})))
  (is (s/valid? ::tls/value (with-meta [1 2 3] {"tag" "v1"}))))

(deftest invalid-numbers
  (testing "integers beyond JS safe range"
    (is (not (s/valid? ::tls/value 9007199254740992)))   ; 2^53
    (is (not (s/valid? ::tls/value -9007199254740992))))
  (testing "non-finite floats"
    (is (not (s/valid? ::tls/value #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))))
    (is (not (s/valid? ::tls/value #?(:clj Double/NEGATIVE_INFINITY :cljs js/-Infinity))))
    (is (not (s/valid? ::tls/value #?(:clj Double/NaN              :cljs js/NaN))))))

(deftest invalid-map-keys
  (is (not (s/valid? ::tls/value {42 "v"})))
  (is (not (s/valid? ::tls/value {[1 2] "v"}))))

(deftest invalid-metadata
  (testing "metadata with unsupported key type"
    (is (not (s/valid? ::tls/value (with-meta {:a 1} {42 "bad"})))))
  (testing "metadata with unsupported value type"
    (is (not (s/valid? ::tls/value (with-meta {:a 1} {:k #?(:clj (Object.) :cljs #js {})}))))))

(deftest invalid-types
  (is (not (s/valid? ::tls/value #?(:clj (Object.) :cljs #js {})))))
