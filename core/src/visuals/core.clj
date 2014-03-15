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




;; ----------------------------------------------------------------------------
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

;; ----------------------------------------------------------------------------
;; A map from name of the top-level spec to view signal containing
;;  all views that are currently visible.
(defonce view-signals (atom {}))

(defn all-views
  "Returns a map of all visible views (not view signals)."
  []
  (->> view-signals
       deref
       (map (juxt first (comp r/getv second)))
       (into {})))


(defn- close-missing-views!
  "Compares the given map of views with the global view-signals map
  and hides all views that are missing in views-map but exist in
  view-signals."
  [views-map]
  (let [missing-view-names (->> view-signals deref keys (remove views-map))]
    (doseq [k missing-view-names]
      (hide! (@view-signals k)))
    (swap! view-signals #(apply dissoc % missing-view-names))))

(declare start!)

(defn- start-new-views!
  "Compares the given map of views with the global view-signals map
  and creates new views for those entries that have their ::vc slot
  set to nil."
  [views-map]
  (let [new-views (->> views-map
                       (map second)
                       (filter #(-> % ::vc nil?)))]
    (doseq [v new-views]
      (-> v r/signal start! show!))))

;; ----------------------------------------------------------------------------

(defn cmap
  "Returns a map of component path to visual component by recursively
   visiting the tree of visual components."
  [vc]
  {:pre [(satisfies? VisualComponent vc)]}
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


(defn all-events
  "Creates a new eventsource that merges all eventsources of all components of the view."
  [view-sig]
  (let [comp-map (-> view-sig r/getv ::comp-map)]
    (->> comp-map vals ; all components
         (mapcat eventsources)
         (map second) ; all eventsources
         (apply r/merge)
         (r/map (partial translate-event toolkit comp-map))))) ; new eventsource that merges all


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
  "Returns the given view signal with an updated version of its map where
  the following data is updated from the view state:
  - ::domain-data
  - ::ui-state
  - ::comp-map
  - ::other-views"
  [view-sig]
  (let [view (r/getv view-sig)
        vc (::vc view)
        domain-data (from-components (::domain-data-mapping view)
                                     (::comp-map view)
                                     (::domain-data view))
        view-state (from-components (::ui-state-mapping view)
                                    (::comp-map view)
                                    (::ui-state view))]
    (r/setv! view-sig
             (-> view
                 (assoc ::comp-map (cmap vc))
                 (assoc ::domain-data domain-data)
                 (assoc ::ui-state view-state)
                 (assoc ::all-views (all-views)))))
  view-sig)


(defn update-to-view!
  "Writes the domain data and ui state from the view signal into 
  the visual components signals.
  Closes views that are missing from ::all-views map when compared
  to the global view-signals map.
  Creates new views that are contained in ::all-views but are
  missing in global view-signals map."
  [view-sig]
  (let [view-map (r/getv view-sig)
        {vc ::vc
         comp-map ::comp-map
         domain-data ::domain-data
         ui-state ::ui-state
         all-views ::all-views} view-map]
    (close-missing-views! all-views)
    (start-new-views! all-views)
    (to-components! (::domain-data-mapping view-map)
                    comp-map
                    domain-data)
    (to-components! (::ui-state-mapping view-map)
                    comp-map
                    ui-state)
    (doseq [evt (::pending-events view-map)]
      (when-let [es (->> evt ::eventtarget (get @view-signals) r/getv ::eventsource)]
        (r/raise-event! es evt)))
    (update! view-sig ::pending-events [])
    view-sig))


(defn- valid-view?
  "Returns true if the view contains at least a VisualComponent."
  [view]
  (when (satisfies? VisualComponent (::vc view))
    view))


(defn- execute-event-handler!
  "Executes a function as event handler that changes the view state.
  f can either be a function or a var pointing to a function.
  The function f is invoked with current view state and the event.
  The result of f is set as new view state into the view-sig.
  The current view state is returned."
  [view-sig f occ]
  (dump (str "calling event-handler " f " for") (dissoc (:event occ) ::payload))
  (let [view (-> view-sig update-from-view! r/getv)
        new-view ((deref-fn f) view (:event occ))]
    (when (valid-view? new-view)
      (r/setv! view-sig new-view)
      (update-to-view! view-sig))
    view-sig))


(defn install-handler!
  "Merges all eventsources of the view into one single eventsource,
  assocs this with key ::eventsource into the view and registers 
  f-or-derefable as listener to the aggregating eventsource.
  If f-or-derefable is omitted it is lookup up with ::handler-fn 
  in view-sig."
  ([view-sig]
     (install-handler! view-sig (::handler-fn (r/getv view-sig))))
  ([view-sig f-or-derefable]
     (let [events (all-events view-sig)
           react-fn (fn react-fn [occ]
                      (execute-event-handler! view-sig f-or-derefable occ))]
       (update! view-sig ::eventsource events)
       (r/subscribe events nil react-fn)
       view-sig)))


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
  "Gets data from the view, validates it, attaches 
  the results to the view and flags any errors."
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
  "Registers a validation listener for all domain-data signals."
  [view-sig]
  (let [{mapping ::domain-data-mapping
         comp-map ::comp-map} (r/getv view-sig)]
    (doseq [{data-path :data-path
             [comp-path signal-key] :signal-path} mapping]
      (let [sig (sigget comp-map comp-path signal-key)]
        (->> sig (r/process-with (fn [v]
                                   (validate! view-sig data-path)))))))
  view-sig)


(defn view
  "Creates a new view that represents the visual components,
  the domain data, ui state, validation results etc."
  [spec & kvs]
  (->> kvs
       (partition 2)
       (map vec)
       (into {::spec spec ; model of the form
              ::vc nil ; root component of the built visual component tree
              ::comp-map {}   ; corresponding map of visual components
              ::domain-data {}          ; business domain data
              ::domain-data-mapping [] ; mapping between signals and business domain data
              ::ui-state {} ; relevant components ui state (enabled, editable, visible and others)
              ::ui-state-mapping [] ; mapping between signals and ui state data
              ::handler-fn (fn [view evt] view) ; event handler function or a derefable containing fn
              ::eventsource nil ; eventsource that merges all eventsources of the view
              ::validation-rule-set {}  ; rule set for validation
              ::validation-results {}   ; current validation results
              ::pending-events []
              ::all-views {}}))); a map from name of the root element of spec to the view  


(defn view-signal
  "Creates a new view-signal and set the given spec.
  The actual building and linking step is done via (start! v)."
  [spec]
  (r/signal (view spec)))


(defn start!
  "Builds and connects all parts of a view.
  The view-sig is registered in the view-signals map using
  the name of the toplevel spec describing the view."
  [view-sig]
  (let [spec (-> view-sig r/getv ::spec)
        vc (build spec)]
    (-> view-sig
        (update! ::vc vc
                 ::comp-map (cmap vc)
                 ::all-views (all-views))
        install-handler!
        update-to-view!
        install-validation!)
    (swap! view-signals assoc (:name spec) view-sig)
    view-sig))


(defn preview
  "Builds and displays the specification of visual components.
  Returns a view signal.
  Intended for use in REPL for rapid prototyping."
  [spec]
  (let [window-spec (if (metam.core/metatype? :visuals.forml/window spec)
                      spec
                      (visuals.forml/window (str "Preview: " (:name spec)) :content spec))]
    (-> (if-let [view-sig (get @view-signals (:name window-spec))]
          (assoc window-spec :into (-> view-sig r/getv ::vc))
          window-spec)
        view-signal
        start!
        show!
        run-now)))


(defmacro modify!
  "Executes the given forms in UI thread and updates the ::comp-map of
   the view.
   Intended for use in REPL for rapid prototyping."
  [view-sig & forms]
  `(let [view# (r/getv ~view-sig)
         result# (v/run-now ~@forms)]
     (r/setv! ~view-sig (assoc view# ::comp-map (v/cmap (::vc view#))))
     result#))


(defn event
  "Returns an event map from the given arguments."
  [sourcepath target payload]
  {::sourcepath (as-vector sourcepath)
   ::eventtarget target
   ::payload payload})


(defn event-matches?
  "Returns true, if path is a suffix of (::sourcepath evt), for example
   (event-matches? [Add :action] {::sourcepath [Actions Add :action]}) yields true, but
   (event-matches? [Add :action] {::sourcepath [Edit :action]}) yields false."
  [path evt]
  (let [sourcepath (::sourcepath evt)]
    (and (>= (count sourcepath) (count path))
         (every? true? (map = (reverse sourcepath) (reverse path))))))


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


(defn close
  "Removes this view from ::all-views. This will hide the visual component
  upon update-to-view."
  [view]
  (if (valid-view? view)
    (update-in view [::all-views] dissoc (-> view ::spec :name))
    view))


(defn create
  "Adds another view to ::all-views. Upon update-to-view this will create 
  a new visual component from the ::spec and make it visible."
  [view other-view]
  (assoc-in view [::all-views (-> other-view ::spec :name)] other-view))


(defn pass-to
  "Passes the view or the message to the view specified by to-view-spec-name
  by enqueuing an event into ::pending-events"
  ([view to-view-spec-name]
     (pass-to view to-view-spec-name view))
  ([view to-view-spec-name msg]
     (update-in view [::pending-events] conj (event (-> view ::spec :name) to-view-spec-name msg))))
