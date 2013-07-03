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
  "Makes the visual component of the view v visible."
  [v]
  (show!* toolkit (::vc v))
  v)


(defn hide!
  "Makes the visual component of the view v invisible."
  [v]
  (hide!* toolkit (::vc v))
  v)


(defn cmap
  "Returns a map of component path to visual component by recursively
   visiting the tree of visual components."
  [vc]
  (let [walk (fn walk [prefix vc]
               (let [p (conj prefix (compname vc))]
                 (concat [[p vc]]
                         (->> vc children (mapcat (partial walk p))))))]
    (->> vc (walk []) (into {}))))


(defn cget
  "Returns the visual component specified by the component path.
   The comp-path can either be a vector (denoting the full path) or
   the last part of the full path (usually a string denoting the compname
   of the visual component."
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


(defn update-from-view
  "Returns an update version of v where domain data and ui state are
   update from the current values of the visual components."
  [v]
  (let [vc (::vc v)]
    (-> v
        (assoc ::comp-map (cmap vc))
        (assoc ::domain-data (from-components (::domain-data-mapping v)
                                              (::comp-map v)
                                              (::domain-data v)))
        (assoc ::ui-state (from-components (::ui-state-mapping v)
                                           (::comp-map v)
                                           (::ui-state v))))))


(defn update-to-view!
  "Writes the domain data and ui state into the visual components signals."
  [v]
  (let [{vc ::vc comp-map ::comp-map} v]
    (to-components! (::domain-data-mapping v)
                    comp-map
                    (::domain-data v))
    (to-components! (::ui-state-mapping v)
                    comp-map
                    (::ui-state v))
    v))


(defn link-event!
  "Adds f as reaction to the visual components eventsource.
   The function f is invoked with the state that the mapping creates from the signals
   of visual components.
   The result of f is set as new state into the visual components signals."
  [v f [comp-path evtsource-key]]
  (let [{vc ::vc
         domaindata-mapping ::domain-data-mapping
         uistate-mapping ::ui-state-mapping} v
        evtsource (-> vc cmap (cget comp-path) eventsources evtsource-key)]
    (->> evtsource
         (r/react-with (fn [occ]
                         (->> v
                              update-from-view
                              f
                              update-to-view!))))
    evtsource))


(defn link-events!
  "Subscribes all functions from the evtsource-fns map to the event sources in v."
  [v evtsource-fns]
  (doseq [[evtsource-path f] evtsource-fns]
    (link-event! v f evtsource-path))
  v)


(defn view
  "Creates a new view that represents the visual components,
   the domain data, ui state, validation results etc.
   The actual building and linking step is done via (start! v)."
  [spec]
  {::spec spec                        ; model of the form
   ::vc nil                           ; root component of the built visual component tree
   ::comp-map {}                      ; corresponding map of visual components
   ::domain-data {}                   ; business domain data
   ::domain-data-mapping {}           ; mapping between signals and business domain data
   ::ui-state {}                      ; relevant components ui state (enabled, editable, visible and others)
   ::ui-state-mapping {}              ; mapping between signals and ui state data
   ::action-fns {}                    ; mapping of eventsource paths to action functions
   ::validation-rule-set {}           ; rule set for validation
   ::validation-results (atom {})})   ; current validation results


(defn start!
  "Builds and connects all parts of a view."
  [v]
  (let [vc (build (::spec v))]
    (-> v
        (assoc ::vc vc
               ::comp-map (cmap vc))
        (link-events! (::action-fns v))
        update-to-view!)))

  
(defn- action-meta
  [var]
  (when-let [a (-> var meta :action)]
    (vector a (var-get var))))


(defn action-fns
  "Returns a map of eventsource paths to functions within the namespace ns
   that have meta data :action attached."
  [ns]
  (->> ns
       ns-map
       vals
       (filter action-meta)
       (map action-meta)
       (into {})))


