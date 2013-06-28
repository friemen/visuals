(ns visuals.core
  "Visuals API"
  (:use [visuals.utils]))


(defonce ^:private toolkit nil)

(defn init-toolkit!
  [tk]
  (alter-var-root #'toolkit (fn [_] tk)))

(defmacro run-later
  [& forms]
  `(run-later* toolkit (fn [] ~@forms)))


(defn run-now*
  [f]
  (let [result (promise)]
    (run-later (deliver result 
                        (try (f) 
                             (catch Exception e (do (.printStackTrace e) e)))))
    @result))


(defmacro run-now
  [& forms]
  `(run-now* (fn [] ~@forms)))


(defn build
  [spec]
  (build* toolkit spec))

(defn show!
  [vc]
  (show!* toolkit vc)
  vc)

(defn hide!
  [vc]
  (hide!* toolkit vc)
  vc)

(defn component-map
  [vc]
  (let [walk (fn walk [prefix vc]
               (let [p (conj prefix (compname vc))]
                 (concat [[p vc]]
                         (->> vc children (mapcat (partial walk p))))))]
    (->> vc (walk []) (into {}))))
