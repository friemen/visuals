(ns visuals.parsform
  "Parsers and formatters for numbers, dates and other types"
  (:require [clojure.string :as s]
            [clj-time.format :as tf])
  (:import [java.text DecimalFormat DecimalFormatSymbols NumberFormat]
           [java.util Locale]))


(defn- number-pattern
  [precision]
  (if (> precision 0)
    (apply str (conj (repeat precision "0") "0."))
    "0"))


(defn format-number
  ([n]
     (format-number 0 n))
  ([precision n]
     (format-number (Locale/getDefault) precision n))
  ([locale precision n]
     (let [df (DecimalFormat. (number-pattern precision) (DecimalFormatSymbols. locale))]
       (.format df n))))


(defn parse-number
  "Returns a number that the text represents."
  ([text]
     (parse-number (Locale/getDefault) text))
  ([locale text]
  (if (s/blank? text) 
    nil
    (if (re-matches #"-?[0-9]+([,.][0-9]+)?" text)
	    (let [nf (NumberFormat/getInstance locale)]
	      (.parse nf text))
      (throw (IllegalArgumentException. (str "'" text "' is not a number")))))))


(defn format-date
  ([d]
     (format-date "dd.MM.yyyy" d))
  ([pattern d]
  (tf/unparse (tf/formatter pattern) d)))


(defn parse-date
  ([text]
     (parse-date "dd.MM.yyyy" text))
  ([pattern text]
     (tf/parse (tf/formatter pattern) text)))
