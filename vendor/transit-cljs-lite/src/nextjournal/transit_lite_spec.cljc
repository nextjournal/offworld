(ns nextjournal.transit-lite-spec
  (:require [#?(:clj clojure.spec.alpha :cljs cljs.spec.alpha) :as s]))

(s/def ::map-key
  (s/or :keyword keyword?
        :string  string?
        :symbol  symbol?))

(s/def ::meta
  (s/nilable (s/and map? (s/every-kv ::map-key ::value))))

(defn- safe-number? [x]
  (if (integer? x)
    ;; JS can't represent integers outside [-2^53, 2^53) without precision loss
    #?(:clj  (< (- (Math/pow 2 53)) x (Math/pow 2 53))
       :cljs (js/Number.isSafeInteger x))
    ;; ##Inf / ##-Inf / ##NaN all stringify to null — silent data corruption
    #?(:clj  (Double/isFinite (double x))
       :cljs (js/isFinite x))))

(s/def ::value
  (s/and
   (fn valid-meta? [x] (s/valid? ::meta (meta x)))
   (s/or :nil     nil?
         :boolean boolean?
         :number  (s/and number? safe-number?)
         :string  string?
         :keyword keyword?
         :symbol  symbol?
         :uuid    uuid?
         :vector  (s/and vector? (s/coll-of ::value))
         :map     (s/and map?    (s/every-kv ::map-key ::value))
         :set     (s/and set?    (s/coll-of ::value)))))
