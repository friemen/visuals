(ns visuals.javafx.builder
  "JavaFX UI Builder"
  (:require [visuals.forml :as f]
            [metam.core :as m])
  (:use [visuals.javafx reactor utils]
        [visuals.utils :only [first-upper]])
  (:import [javafx.scene.control Button ChoiceBox Label ListView TableView TableColumn TextField]
           [javafx.scene Scene]
           [javafx.stage Stage]
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


(defn- set-signals!
  "Installs a map with all signals into the custom properties of a visual component."
  [component & propnames]
  (->> propnames
       (for-props component prop-signal)
       (putp! component :signals)))


(defn- set-eventsources!
  "Installs a map with all eventsources into the custom properties of a visual component."
  [component & propnames]
  (->> propnames
       (for-props component comp-eventsource)
       (putp! component :eventsources)))


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


(defmulti build
  (fn [spec]
    (m/metatype spec))
  :hierarchy #'f/forml-hierarchy)


(defmethod build :default
  [spec]
  (throw (IllegalArgumentException. (str "Cannot build type '" (m/metatype spec) "'"))))


(defmethod build :visuals.forml/button
  [spec]
  (doto (make Button spec)
    (set-signals! "disabled" "focused" "text")
    (set-eventsources! "onAction")
    (set-icon! (:icon spec))
    (.setText (:text spec))))


(defmethod build :visuals.forml/dropdownlist
  [spec]
  (doto (make ChoiceBox spec)
    (set-signals! "disabled" "focused" "items[]" "value")))


(defmethod build :visuals.forml/label
  [spec]
  (doto (make Label spec)
    (set-icon! (:icon spec))
    (.setText (:text spec))))


(defmethod build :visuals.forml/list
  [spec]
  (let [component (make ListView spec)
        signal-map (merge
                    (for-props component prop-signal ["disabled" "focused" "items[]"])
                    (for-props component selection-signal ["selected"]))]
    (putp! component :signals signal-map)
    component))


(defmethod build :visuals.forml/panel
  [spec]
  (make-panel spec))


(defmethod build :visuals.forml/table
  [spec]
  (let [component (make TableView spec)
        columns (.getColumns component)
        signal-map (merge
                    (for-props component prop-signal ["disabled" "focused" "items[]"])
                    (for-props component selection-signal ["selected"]))] 
    (doseq [tc-spec (:columns spec)]
      (.add columns (make-column tc-spec)))
    (putp! component :signals signal-map)
    component))


(defmethod build :visuals.forml/textfield
  [spec]
  (doto (make TextField spec)
    (set-signals! "disabled" "focused" "text" "editable")
    (set-eventsources! "onAction")))


(defmethod build :visuals.forml/window
  [spec]
  (let [content (build (:content spec))
        scene (doto (Scene. content 500 500)
                (putp! :spec spec))
        root-stage (root-window)]
    (doto (if (-> root-stage .getScene)
            (doto (Stage.) (.initOwner root-stage))
            root-stage)
      (.setScene scene)
      (.setTitle (:title spec))
      (putp! :spec spec)
      (set-signals! "title")
      (set-eventsources! "onCloseRequest"))))

