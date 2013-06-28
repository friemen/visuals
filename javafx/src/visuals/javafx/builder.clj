(ns visuals.javafx.builder
  "JavaFX UI Builder"
  (:require [visuals.forml :as f]
            [metam.core :as m])
  (:use [visuals.javafx reactor utils])
  (:import [javafx.scene.control Button Label TableView TableColumn TextField]
           [javafx.scene Scene]
           [javafx.stage Stage]
           [visuals.javafx.reactor PropertyBasedSignal]
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


(defn- add-component!
  [owner spec]
  (let [c (build spec)
        lyhint (:lyhint spec)]
    (when-let [lt (:label spec)]
      (add-label! owner c lt (:labelyhint spec)))
    (.add owner c lyhint)
    c))


(defn- set-signals!
  [component & propnames]
  (->> propnames
       (for-props component prop-signal)
       (putp! component :signals)))


(defn- set-eventsources!
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


(defmulti build
  (fn [spec]
    (m/metatype spec))
  :hierarchy #'f/forml-hierarchy)


(defmethod build :default
  [spec]
  (throw (IllegalArgumentException. (str "Cannot build type " (m/metatype spec)))))


(defmethod build :visuals.forml/panel
  [spec]
  (make-panel spec))


(defmethod build :visuals.forml/textfield
  [spec]
  (doto (make TextField spec)
    (set-signals! "disabled" "focused" "text" "editable")
    (set-eventsources! "OnAction")))


(defmethod build :visuals.forml/label
  [spec]
  (doto (make Label spec)
    (.setText (:text spec))))


(defmethod build :visuals.forml/button
  [spec]
  (doto (make Button spec)
    (.setText (:text spec))))


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
      (set-eventsources! "OnCloseRequest"))))

