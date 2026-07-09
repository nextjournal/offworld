(ns nextjournal.transit-lite-test
  (:require [cljs.test :refer [deftest is testing run-tests]]
            [nextjournal.transit-lite :as tx]))

(defn roundtrip [x]
  (tx/read-str (tx/write-str x)))

;; ---------------------------------------------------------------------------
;; Primitives

(deftest test-nil
  (is (nil? (roundtrip nil))))

(deftest test-booleans
  (is (true?  (roundtrip true)))
  (is (false? (roundtrip false))))

(deftest test-numbers
  (is (= 0     (roundtrip 0)))
  (is (= 42    (roundtrip 42)))
  (is (= -7    (roundtrip -7)))
  (is (= 3.14  (roundtrip 3.14))))

(deftest test-strings
  (is (= ""      (roundtrip "")))
  (is (= "hello" (roundtrip "hello")))
  ;; strings starting with ~ or ^ must be escaped
  (is (= "~foo"  (roundtrip "~foo")))
  (is (= "^foo"  (roundtrip "^foo")))
  (is (= "~~"    (roundtrip "~~"))))

(deftest test-keywords
  (is (= :foo    (roundtrip :foo)))
  (is (= :ns/foo (roundtrip :ns/foo))))

(deftest test-symbols
  (is (= 'foo    (roundtrip 'foo)))
  (is (= 'ns/foo (roundtrip 'ns/foo))))

(deftest test-uuid
  (let [id (random-uuid)]
    (is (= id (roundtrip id)))))

;; ---------------------------------------------------------------------------
;; Collections

(deftest test-vectors
  (is (= []        (roundtrip [])))
  (is (= [1 2 3]   (roundtrip [1 2 3])))
  (is (= [:a "b"]  (roundtrip [:a "b"]))))

(deftest test-maps
  (is (= {}               (roundtrip {})))
  (is (= {:a 1}           (roundtrip {:a 1})))
  (is (= {:a 1 :b 2}      (roundtrip {:a 1 :b 2})))
  (is (= {"str" 42}       (roundtrip {"str" 42})))
  (is (= {:a {:b {:c 3}}} (roundtrip {:a {:b {:c 3}}}))))

(deftest test-sets
  (is (= #{}      (roundtrip #{})))
  (is (= #{1 2 3} (roundtrip #{1 2 3})))
  (is (= #{:a :b} (roundtrip #{:a :b}))))

;; ---------------------------------------------------------------------------
;; Nested / mixed

(deftest test-nested
  (is (= {:items [1 2 3] :tags #{:a :b} :name "foo"}
         (roundtrip {:items [1 2 3] :tags #{:a :b} :name "foo"})))
  (is (= [{:id 1 :v :x} {:id 2 :v :y}]
         (roundtrip [{:id 1 :v :x} {:id 2 :v :y}]))))

;; ---------------------------------------------------------------------------
;; Metadata

(deftest test-with-meta
  (let [v (with-meta {:a 1} {:source :cache})
        rt (tx/read-str (tx/write-str (tx/write-meta v)))]
    (is (= {:a 1} rt))
    (is (= {:source :cache} (meta rt)))))

;; ---------------------------------------------------------------------------
;; Cache — repeated keywords across a payload

(deftest test-cache-repeated-keys
  ;; Same keyword appearing as map key many times should round-trip correctly
  ;; regardless of whether the server used cache compression.
  (let [data (mapv (fn [i] {:id i :type :vehicle}) (range 20))]
    (is (= data (roundtrip data)))))

;; ---------------------------------------------------------------------------

(defn -main [& _]
  (run-tests 'nextjournal.transit-lite-test))
