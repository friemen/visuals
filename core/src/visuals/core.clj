(ns visuals.core
  "Visuals API"
  (:require [reactor.core :as r]
            [reactor.execution]
            [metam.core]
            [visuals.forml]
            [examine.core :as e]
            [parsargs.core :as p]
            [visuals.utils :refer :all]))


(defprotocol VisualComponent
  (compname [vc])
  (comptype [vc])
  (parent [vc])
  (set-error! [vc on])
  (children [vc])
  (eventsources [vc])
  (signals [vc]))


(defonce toolkit nil)

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


(def ui-thread (reify reactor.execution.Executor
                 (schedule [_ f] (visuals.utils/run-later* (var-get #'toolkit) f))
                 (cancel [_] false)))

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
   the last part of the full path (usually a string denoting the
   component name of the visual component."
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
  "Returns the signal specified by comp-path and signal-key within the comp-map."
  [comp-map comp-path signal-key]
  (-> comp-map (cget comp-path) signals signal-key))


(defn component
  "Returns the toolkits implementation of the visual component that comp-path points to."
  [view-sig comp-path]
  (-> view-sig r/getv ::comp-map (cget comp-path)))

(defn signal
  "Returns the signal denoted by signal-key of the visual component that comp-path points to."
  [view-sig comp-path signal-key]
  (-> view-sig r/getv ::comp-map (sigget comp-path signal-key)))


(defn eventsource
  "Returns the eventsource denoted by evtsrc-key of the visual component that comp-path points to."
  [view-sig comp-path evtsrc-key]
  (-> view-sig r/getv ::comp-map (cget comp-path) eventsources evtsrc-key))


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
   data map."
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
  "Returns the given view signal with an updated version of view-map where
   domain data and ui state are updated from the current values of the
   visual components."
  [view-sig]
  (let [view-map (r/getv view-sig)
        vc (::vc view-map)
        domain-data (from-components (::domain-data-mapping view-map)
                                     (::comp-map view-map)
                                     (::domain-data view-map))
        view-state (from-components (::ui-state-mapping view-map)
                                    (::comp-map view-map)
                                    (::ui-state view-map))]
    (r/setv! view-sig
             (-> view-map
                 (assoc ::comp-map (cmap vc))
                 (assoc ::domain-data domain-data)
                 (assoc ::ui-state view-state))))
  view-sig)


(defn update-to-view!
  "Writes the domain data and ui state from the view signal into the visual
   components signals."
  [view-sig]
  (let [view-map (r/getv view-sig)
        {vc ::vc comp-map ::comp-map} view-map
        domain-data (::domain-data view-map)
        ui-state (::ui-state view-map)]
    (to-components! (::domain-data-mapping view-map)
                    comp-map
                    domain-data)
    (to-components! (::ui-state-mapping view-map)
                    comp-map
                    ui-state)
    view-sig))


(defn set-action!
  "Sets f as reaction to the visual components event source.
   The function f is invoked with current view state as single argument.
   The result of f is set as new view state into the view-sig."
  [view-sig f comp-path evtsource-key]
  (let [vc (-> view-sig r/getv ::vc)
        evtsource (-> vc cmap (cget comp-path) eventsources evtsource-key)]
    (r/unsubscribe evtsource nil) ; remove all existing actions
    (->> evtsource (r/react-with (fn [occ]
                                   (some->> view-sig
                                            update-from-view!
                                            r/getv
                                            f
                                            (r/setv! view-sig))
                                   (update-to-view! view-sig))))
    evtsource))


(defn- connect-actions-to-eventsources!
  "Subscribes all functions from the evtsource-fns map to the event sources in v."
  [view-sig]
  (doseq [[[comp-path evtsource-key] f] (-> view-sig r/getv ::action-fns)]
    (set-action! view-sig f comp-path evtsource-key))
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
        connect-actions-to-eventsources!
        update-to-view!
        install-validation!)))

(defn preview
  "Builds and displays the specification of visual components.
   Intended for use in REPL for rapid prototyping."
  [spec]
  (let [window-spec (if (metam.core/metatype? :visuals.forml/window spec)
                      spec
                      (visuals.forml/window (str "Preview: " (:name spec)) :content spec))]
    (-> window-spec view-signal start! show! run-now)))


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
  "Returns a sequence of mappings from data path to signal path,
   with optional formatter and parser. Example usage:
   (v/mapping :name    [\"Name\" :text]
              :age     [\"Birthday\" :text] pf/format-date pf/parse-date)"
  [& args]
  (p/parse mapping-parser args))
