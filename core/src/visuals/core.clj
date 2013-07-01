(ns visuals.core
  "Visuals API"
  (:require [reactor.core :as r])
  (:use [visuals.utils]))


(defprotocol VisualComponent
  (compname [vc])
  (comptype [vc])
  (parent [vc])
  (children [vc])
  (eventsources [vc])
  (signals [vc]))



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

(defn cmap
  [vc]
  (let [walk (fn walk [prefix vc]
               (let [p (conj prefix (compname vc))]
                 (concat [[p vc]]
                         (->> vc children (mapcat (partial walk p))))))]
    (->> vc (walk []) (into {}))))

(defn cget
  [comp-map comp-path]
  (if (vector? comp-path)
    (get comp-map comp-path)
    (let [ks (->> comp-map
                  keys
                  (filter #(= comp-path (last %))))]
      (if (> (count ks) 1)
        (throw (IllegalArgumentException. (str "Key '" comp-path "' is not unique among " (keys comp-map))))
        (get comp-map (first ks))))))


(defn- as-vector
  [x]
  (if (vector? x) x (if (coll? x) (vec x) (vector x))))

(defn- sigget
  [comp-map comp-path signal-key]
  (-> comp-map (cget comp-path) signals signal-key))


(defn to-components!
  "Merges the given data into the signals contained in comp-map."
  [mapping comp-map data]
  (doseq [[data-path [comp-path signal-key]] (seq mapping)]
    (r/setv! (sigget comp-map comp-path signal-key)
             (get-in data (as-vector data-path)))))

(defn from-components
  "Associates the values of the signals contained in comp-map into the given
   map."
  [mapping comp-map data]
  (let [sig-values (for [[data-path [comp-path signal-key]] (seq mapping)]
                     (vector (as-vector data-path)
                               (r/getv (sigget comp-map comp-path signal-key))))]
    (reduce (fn [accu [data-path value]]
              (assoc-in accu data-path value))
            data
            sig-values)))


(defn link-event!
  "Adds f as reaction to the visual components eventsource.
   The function f is invoked with the state that the mapping creates from the signals
   of visual components.
   The result of f is set as new state into the visual components signals."
  [vc f mapping [comp-path evtsource-key]]
  (let [evtsource (-> vc cmap (cget comp-path) eventsources evtsource-key)]
    (->> evtsource
         (r/react-with (fn [occ]
                         (let [comp-map (cmap vc)]
                           (->> {}
                                (from-components mapping comp-map)
                                f
                                (to-components! mapping comp-map))))))
    evtsource))


(defn- action-meta
  [var]
  (when-let [a (-> var meta :action)]
    (vector (var-get var) a)))


(defn link-events!
  "Links all functions from the ns namespace that have :action metadata
   to the event sources of the visual components of vc."
  [vc ns mapping]
  (doseq [[f evtsource-path] (->> ns
                                  ns-map
                                  vals
                                  (filter action-meta)
                                  (map action-meta))]
    (link-event! vc f mapping evtsource-path))
  vc)

