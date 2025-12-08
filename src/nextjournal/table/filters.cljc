(ns nextjournal.table.filters
  (:require [clojure.string :as str])
  #?(:cljs (:refer-global :only [Error] :rename {Error Exception})))

(defn substring? [needle s]
  (assert (string? needle))
  (and (string? s)
       (str/includes? s needle)))

(defn build-pred [{:as filter :keys [kind value]}]
  (case kind
    ::equals (partial = value)
    ::substring (partial substring? value)
    ::matches (partial re-matches value)))

(defn ->pred [filter]
  (or (:pred filter)
      (when (:->pred filter)
        ((:->pred filter) filter))
      (build-pred filter)))

(defn build-equals-filter [x]
  {:value x
   :label (str "Is " (pr-str x))
   :kind ::equals})

(comment
  ((->pred (build-equals-filter "foo")) "foo"))

(defn build-substring-filter [needle]
  (assert (string? needle))
  {:value needle
   :label (str "Contains " (pr-str needle))
   :kind ::substring})

(comment
  ((->pred (build-substring-filter "foo")) "foobar"))

(defn glob-regex [glob]
  (try
    (as-> glob %
      (str/lower-case %)
      (str/replace % #"[\.\\\(\)\{\}\^\$\+\|]" "\\.")
      (str/replace % #"\[!" "[^")
      (str/replace % "*" ".*")
      (str/replace % "?" ".")
      (str "^" % "$")
      #?(:clj (re-pattern %)
         :cljs (js/RegEx % "u")))
    (catch Exception e
      nil)))

(comment
  (glob-regex "*foo*"))


(defn build-glob-filter [glob]
  (assert (string? glob))
  (when-let [regex (glob-regex glob)]
    {:value regex
     :label (str "Matches " (pr-str glob))
     :kind ::matches}))

(comment
  ((->pred (build-glob-filter "foo*")) "foobar"))

(defn text->filter [text]
  (let [text (str/trim text)]
    (condp re-matches text
      #_#_#_#_
      #"-.*[\*\?\[].*"
      (let [text' (str/trim (subs text 1))]
        (when-some [re (glob-regex text')]
          {:filter.omnibox/type  :filter.omnibox.type/not-glob
           :filter.omnibox/label text'
           :filter.omnibox/regex re}))

      #"-.*[^\s].*"
      (let [text' (str/trim (subs text 1))]
        {:filter.omnibox/type             :filter.omnibox.type/not-substring
         :filter.omnibox/label            text'
         :filter.omnibox/normalized-value (str/lower-case text')})

      #".*[\*\?\[].*"
      (build-glob-filter text)

      #".*[^\s]+.*"
      (build-substring-filter text)

      nil)))

(comment
  (text->filter "foo")
  (text->filter "foo*"))
