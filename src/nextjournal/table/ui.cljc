(ns nextjournal.table.ui
  (:require [nextjournal.table.ui.omnibox :as omnibox-ui]))

(defn render [state]
  (omnibox-ui/omnibox state))
