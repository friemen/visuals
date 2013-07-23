(ns visuals.parsform-test
  (:use [clojure.test]
        [visuals.parsform])
  (:require [clj-time.core :as tm]))


(deftest format-number-test
  (are [r n p] (= r (format-number (java.util.Locale. "en") p n))
       "0"       0    0
       "42.00"  42    2
       "47.110" 47.11 3))


(deftest parse-number-test
  (are [r text] (= (parse-number (java.util.Locale. "en") text))
       nil  ""
       4711 "4711"
       -12.3 "-0012.300"))


(deftest format-date-test
  (are [r d p] (= r (format-date p (apply tm/date-time d)))
       "23.07.2013" [2013 07 23] "dd.MM.yyyy"
       "July 2013" [2013 07 23] "MMMM yyyy"))


(deftest parse-date-test
  (are [r t p] (= (apply tm/date-time r) (parse-date p t))
       [2012 12 31] "31.12.2012" "dd.MM.yyyy"
       [2009 10 31] "Oct 31 09" "MMM dd yy"))
