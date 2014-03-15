(ns visuals.javafx.builder
  "JavaFX UI Builder"
  (:require [visuals.forml :as f]
            [metam.core :as m]
            [visuals.utils :refer [first-upper dump]]
            [visuals.core :as v]
            [reactor.core :as r]
            [visuals.javafx.reactor :refer :all]
            [visuals.javafx.utils :refer :all])
  (:import [javafx.scene.control Button CheckBox ChoiceBox Label ListView
                                 RadioButton TableView TableColumn TextField
                                 ToggleGroup]
           [javafx.scene Scene]
           [javafx.stage Stage Modality]
           [javafx.util Callback]
           [javafx.beans.property ReadOnlyObjectWrapper]
           [visuals.javafx.reactor PropertyBasedSignal SelectionModelBasedSignal]
           [org.tbee.javafx.scene.layout MigPane]))


;; builder stuff

(declare build add-component!)

(defn- make
  [clazz spec]
  (doto (.newInstance clazz)
    (.setId (:name spec))
    (putp! :spec spec)))


(defn- add-label!
  [owner c text lyhint]
  (let [name (str (.getId c) "-label")
        label (add-component! owner (f/label name :text text :lyhint lyhint))]
    (.setLabelFor label c)
    label))


(defn add-component!
  [owner spec]
  (let [c (build spec)
        lyhint (:lyhint spec)]
    (when-let [lt (:label spec)]
      (add-label! owner c lt (:labelyhint spec)))
    (.add owner c lyhint)
    c))


(defn set-icon!
  [labeled name]
  (if name (.setGraphic labeled (image name))))


(defn- set-signal-map!
  [component signal-map]
  (putp! component :signals signal-map))


(defn- set-eventsource-map!
  [component eventsource-map]
  (putp! component :eventsources eventsource-map))


(defn- define-signals!
  [component & propnames]
  (->> propnames
       (binding-specs component prop-signal)
       make-reactive-map
       (set-signal-map! component)))


(defn- make-panel
  [spec]
  (let [p (MigPane. (:lygeneral spec) 
                    (:lycolumns spec)
                    (:lyrows spec))]
	  (.setId p (:name spec))
	  (putp! p :spec spec)
    (doseq [spec (:components spec)]
      (add-component! p spec))
    p))


(defn- make-column
  [spec]
  (let [tc (TableColumn. (:title spec))]
    (.setCellValueFactory tc
                          (reify Callback
                            (call [_ r]
                              (ReadOnlyObjectWrapper. (-> r .getValue (get (:key spec)))))))
    tc))


(defn- make-radiobutton
  [spec-or-string]
  (if (string? spec-or-string)
    (f/radio spec-or-string)
    spec-or-string))


(defmulti build
  (fn [spec]
    (m/metatype spec))
  :hierarchy #'f/forml-hierarchy)


(defmethod build :default
  [spec]
  (throw (IllegalArgumentException. (str "Cannot build type '" (m/metatype spec) "'"))))


(defmethod build :visuals.forml/button
  [spec]
  (let [component (doto (make Button spec)
                    (set-icon! (:icon spec))
                    (.setText (:text spec)))]
    (define-signals! component "disabled" "focused" "text")
    (->> (binding-spec component comp-eventsource :action "onAction")
         vector
         make-reactive-map
         (set-eventsource-map! component))
    component))


(defmethod build :visuals.forml/buttongroup
  [spec]
  (let [group (ToggleGroup.)
        buttons (->> (:buttons spec)
                     (map make-radiobutton)
                     vec)
        panel-spec (f/panel (:name spec)
                            :components buttons
                            :lygeneral (if (= :vertical (:orientation spec))
                                         "flowy"
                                         ""))
        panel (make-panel panel-spec)]
    ;;TODO add signal for selection
    (doseq [b (visuals.core/children panel)]
      (.setToggleGroup b group))
    panel))


(defmethod build :visuals.forml/checkbox
  [spec]
  (doto (make CheckBox spec)
    (.setText (:text spec))
    (define-signals! "disabled" "focused" "selected")))


(defmethod build :visuals.forml/dropdownlist
  [spec]
  (let [component (make ChoiceBox spec)
        signal-map (make-reactive-map
                    (conj
                     (binding-specs component prop-signal ["disabled" "focused" "value"])
                     (binding-spec component list-signal :items "items")))]
    (set-signal-map! component signal-map)
    component))


(defmethod build :visuals.forml/label
  [spec]
  (doto (make Label spec)
    (set-icon! (:icon spec))
    (.setText (:text spec))
    (define-signals! "text")))


(defmethod build :visuals.forml/listbox
  [spec]
  (let [component (make ListView spec)
        signal-map (make-reactive-map
                    (conj
                     (binding-specs component prop-signal ["disabled" "focused"])
                     (binding-spec component list-signal :items "items")
                     (binding-spec component selection-signal :selected "selected")))]
    (set-signal-map! component signal-map)
    component))


(defmethod build :visuals.forml/panel
  [spec]
  (make-panel spec))


(defmethod build :visuals.forml/radio
  [spec]
  (doto (make RadioButton spec)
    (.setText (:text spec))
    (define-signals! "disabled" "focused" "text")))


(defmethod build :visuals.forml/table
  [spec]
  (let [component (make TableView spec)
        columns (.getColumns component)
        signal-map (make-reactive-map
                    (conj
                     (binding-specs component prop-signal ["disabled" "focused"])
                     (binding-spec component list-signal :items "items")
                     (binding-spec component selection-signal :selected "selected")))] 
    (doseq [tc-spec (:columns spec)]
      (.add columns (make-column tc-spec)))
    (set-signal-map! component signal-map)
    component))


(defmethod build :visuals.forml/textfield
  [spec]
  (let [component (doto (make TextField spec))]
    (define-signals! component "disabled" "focused" "text" "editable")
    (->> (binding-spec component comp-eventsource :action "onAction")
         vector
         make-reactive-map
         (set-eventsource-map! component))
    component))



(defn- make-stage
  "Returns either the root stage if it wasn't already initialized with a scene,
  or a new stage that is owned by the root stage, or an explicitly specified
  owner."
  [spec]
  (let [root-stage (root-window)]
    (if (-> root-stage .getScene)
      (doto (Stage.)
        (.initOwner (dump "using owner"
                          (if-let [owner (some-> v/view-signals
                                                 deref
                                                 (get (:owner spec))
                                                 r/getv
                                                 ::v/vc)]
                            owner
                            root-stage)
                          #(.getTitle %)))
        (.initModality (case (:modality spec)
                         :window Modality/WINDOW_MODAL
                         :application Modality/APPLICATION_MODAL
                         Modality/NONE)))
      root-stage)))


(defmethod build :visuals.forml/window
  [spec]
  (let [content (build (:content spec))
        scene (doto (Scene. content)
                (putp! :spec spec))]
    (let [component (doto (if (contains? spec :into)
                            (:into spec)
                            (make-stage spec))
                      (.setScene scene)
                      (.sizeToScene)
                      (.setTitle (:title spec))
                      (putp! :spec (dissoc spec :into)))]
      (define-signals! component "title")
      (->> (binding-spec component comp-eventsource :close "onCloseRequest")
           vector
           make-reactive-map
           (set-eventsource-map! component))
      component)))

