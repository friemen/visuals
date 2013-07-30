(ns visuals.core
  "Visuals API"
  (:require [reactor.core :as r]
            [examine.core :as e]
            [parsargs.core :as p])
  (:use [visuals.utils]))


(defprotocol VisualComponent
  (compname [vc])
  (comptype [vc])
  (parent [vc])
  (set-error! [vc on])
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
  [view-sig]
  (show!* toolkit (-> view-sig r/getv ::vc))
  view-sig)


(defn hide!
  "Makes the visual component of the view v invisible."
  [view-sig]
  (hide!* toolkit (-> view-sig r/getv ::vc))
  view-sig)


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
  (doseq [{format :formatter
           data-path :data-path
           [comp-path signal-key] :signal-path} mapping]
    (r/setv! (sigget comp-map comp-path signal-key)
             (->> data-path
                  as-vector
                  (get-in data)
                  format))))


(defn- text-or-value
  [parser-fn s]
  (try (parser-fn s) (catch Exception ex s)))


(defn from-components
  "Associates the values of the signals contained in comp-map into the given
   map."
  [mapping comp-map data]
  (let [sig-values (for [{parse :parser
                          data-path :data-path
                          [comp-path signal-key] :signal-path} mapping]
                     (vector (as-vector data-path)
                             (->> (sigget comp-map comp-path signal-key)
                                  r/getv
                                  (text-or-value parse))))]
    (reduce (fn [accu [data-path value]]
              (assoc-in accu data-path value))
            data
            sig-values)))


(defn update!
  "Updates the view-map within the view-signal by assoc'ing the given
   key-value-pairs."
  [view-sig & kvs]
  (r/setv! view-sig (apply assoc (conj kvs (r/getv view-sig))))
  view-sig)


(defn update-from-view!
  "Returns an updated version of view-map where domain data and ui state are
   updated from the current values of the visual components."
  [view-sig]
  (let [view-map (r/getv view-sig)
        vc (::vc view-map)]
    (r/setv! view-sig
             (-> view-map
                 (assoc ::comp-map (cmap vc))
                 (assoc ::domain-data (from-components (::domain-data-mapping view-map)
                                                       (::comp-map view-map)
                                                       (::domain-data view-map)))
                 (assoc ::ui-state (from-components (::ui-state-mapping view-map)
                                                    (::comp-map view-map)
                                                    (::ui-state view-map))))))
  view-sig)


(defn update-to-view!
  "Writes the domain data and ui state from the view-map into the visual
   components signals."
  [view-sig]
  (let [view-map (r/getv view-sig)
        {vc ::vc comp-map ::comp-map} view-map]
    (to-components! (::domain-data-mapping view-map)
                    comp-map
                    (::domain-data view-map))
    (to-components! (::ui-state-mapping view-map)
                    comp-map
                    (::ui-state view-map))
    view-sig))


(defn- link-event!
  "Adds f as reaction to the visual components eventsource.
   The function f is invoked with the state that the mapping creates from
   the signals of visual components.
   The result of f is set as new state into the visual components signals."
  [view-sig f [comp-path evtsource-key]]
  (let [vc (-> view-sig r/getv ::vc)
        evtsource (-> vc cmap (cget comp-path) eventsources evtsource-key)]
    (->> evtsource (r/react-with (fn [occ]
                                   (->> view-sig
                                        update-from-view!
                                        r/getv
                                        f
                                        (r/setv! view-sig))
                                   (update-to-view! view-sig))))
    evtsource))


(defn- link-events!
  "Subscribes all functions from the evtsource-fns map to the event sources in v."
  [view-sig]
  (doseq [[evtsource-path f] (-> view-sig r/getv ::action-fns)]
    (link-event! view-sig f evtsource-path))
  view-sig)


(defn- components-with-invalid-data
  "Returns a set of all visual components that contain invalid data."
  [view-sig]
  (let [{vrs ::validation-results
         comp-map ::comp-map
         mapping ::domain-data-mapping} (r/getv view-sig)
         msgs (e/messages vrs)]
    (->> mapping
         (filter #(msgs (:data-path %)))
         (map #(cget comp-map (-> % :signal-path first)))
         set)))


(defn- validate!
  [view-sig data-path]
  (update-from-view! view-sig)
  (let [{current-results ::validation-results
         rule-set ::validation-rule-set
         domain-data ::domain-data
         comp-map ::comp-map} (r/getv view-sig)
         new-results (e/validate (e/sub-set rule-set data-path) domain-data)]
    (update! view-sig ::validation-results (e/update current-results new-results))
    (let [invalid-vcs (components-with-invalid-data view-sig)]
      (doseq [[comp-path vc] comp-map]
        (set-error! vc (invalid-vcs vc))))
    view-sig))


(defn- install-validation!
  [view-sig]
  (let [{mapping ::domain-data-mapping
         comp-map ::comp-map} (r/getv view-sig)]
    (doseq [{data-path :data-path
             [comp-path signal-key] :signal-path} mapping]
      (let [sig (sigget comp-map comp-path signal-key)]
        (->> sig (r/process-with (fn [v]
                                   (validate! view-sig data-path)))))))
  view-sig)


(defn view-signal
  "Creates a new view that represents the visual components,
   the domain data, ui state, validation results etc.
   The actual building and linking step is done via (start! v)."
  [spec]
  (r/signal
   {::spec spec                        ; model of the form
    ::vc nil                           ; root component of the built visual component tree
    ::comp-map {}                      ; corresponding map of visual components
    ::domain-data {}                   ; business domain data
    ::domain-data-mapping []           ; mapping between signals and business domain data
    ::ui-state {}                      ; relevant components ui state (enabled, editable, visible and others)
    ::ui-state-mapping []              ; mapping between signals and ui state data
    ::action-fns {}                    ; mapping of eventsource paths to action functions
    ::validation-rule-set {}           ; rule set for validation
    ::validation-results {}}))         ; current validation results


(defn start!
  "Builds and connects all parts of a view."
  [view-sig]
  (let [vc (build (-> view-sig r/getv ::spec))]
    (-> view-sig
        (update! ::vc vc
                 ::comp-map (cmap vc))
        link-events!
        update-to-view!
        install-validation!)))

  
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


(def ^:private mapping-parser
  (p/some
   (p/sequence :data-path (p/alternative (p/value keyword?) (p/value vector?))
               :signal-path (p/value vector?)
               :formatter (p/optval fn? identity)
               :parser (p/optval fn? identity))))

(defn mapping
  [& args]
  (p/parse mapping-parser args))
