(ns visuals.utils
  "Abstractions and common, toolkit-independent, internal functions"
  (:require [clojure.string :as s]))

(defprotocol Toolkit
  (build* [tk spec])
  (show!* [tk vc])
  (hide!* [tk vc])
  (translate-event [tk comp-map event])
  (run-later* [tk f]))


(defn invoke
  ([instance methodname]
     (invoke instance methodname nil []))
  ([instance methodname parameter-types parameter-values]
     (-> instance
         class
         (.getMethod methodname (into-array java.lang.Class parameter-types))
         (.invoke instance (object-array parameter-values)))))


(defn first-upper
  "Returns string s with the first char in uppercase."
  [s]
  (if (s/blank? s)
    ""
    (apply str (s/upper-case (first s)) (rest s))))


(defn first-lower
  "Returns string s with the first char in uppercase."
  [s]
  (if (s/blank? s)
    ""
    (apply str (s/lower-case (first s)) (rest s))))


(defn dump
  "Logs a DEBUG text to the console and returns the value v."
  ([comment expr]
     (dump comment expr identity))
  ([comment expr f]
     (println "DEBUG" comment (f expr))
     expr))


(defn before
  "Returns prefix of xs that are before y, if y is contained in xs,
  or nil, if y is not in xs.
  Example: (before :b [:a :b :c]) => [:a]"
  [y xs]
  (loop [xs (seq xs), prefix []]
    (if-let [x (first xs)]
      (if (= x y)
        prefix
        (recur (rest xs) (conj prefix (first xs)))))))


(defn index
  "Returns a seq of pairs [x i] where i is the index starting at offset.
  If offset is ommitted then the first x has index 0.
  Example: (index 3 [:a :b :c]) => ([:a 3] [:b 4] [:c 5])"
  ([xs]
     (index 0 xs))
  ([offset xs]
     (map vector xs (range offset (+ offset (count xs))))))


(defn diff
  "Calculates the diff of two seqs xs and ys and returns a map with the elements
  that were removed from xs (they don't appear in ys, denoted by key :-) and that
  were added to ys (they don't appear in xs, denoted by key :+).
  Example: (diff [:a :b :c :d] [:A :B :a :b :c]) => {:- [[:d 3]], :+ [[:A 0] [:B 1]]}"
  [xs ys]
  (loop [xsi (index xs), ys (seq ys), added [], removed []]
    (if-let [[x i] (first xsi)]
      (if-let [skipped (before x ys)]
        (recur (rest xsi) (drop (inc (count skipped)) ys) (concat added (index i skipped)) removed)
        (recur (rest xsi) ys added (conj removed [x i])))
      {:- (vec removed)
       :+ (vec (concat added (index (+ (count added) (- (count xs) (count removed))) ys)))})))


(defn into-list!
  "Synchronizes list with xs, so that list is changed by single 
   removals/insertions until it contains all of xs items."
  [list xs]
  (let [{removals :- inserts :+} (diff list xs)]
    (doseq [[v i] (reverse removals)]
      (.remove #^java.util.List list (int i)))
    (doseq [[v i] inserts]
      (.add list i v)))
  list)


(defn as-vector
  "Returns a vector. 
  If x is a scalar it is wrapped in a vector.
  If x is a collection it is transformed to a vector."
  [x]
  (if (vector? x)
    x
    (if (coll? x)
      (vec x)
      (vector x))))


(defn deref-fn
  "Returns a function. The arg is either itself a function or a IDeref that
  contains function."
  [f-or-derefable]
  {:post [(fn? %)]}
  (cond
   (fn? f-or-derefable) f-or-derefable
   (instance? clojure.lang.IDeref f-or-derefable) (deref f-or-derefable)
   :else (throw (IllegalArgumentException. "Argument is neither a fn nor a ref-type"))))
