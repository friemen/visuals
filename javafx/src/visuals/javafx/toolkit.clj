(ns visuals.javafx.toolkit
  "JavaFX implementations of core abstractions"
  (:import [javafx.application Platform]
           [javafx.scene Node]
           [javafx.stage Stage]
           [org.tbee.javafx.scene.layout MigPane])
  (:require [visuals.javafx.builder :as builder]
            [visuals.javafx.application]
            [metam.core :as m])
  (:use [visuals core utils]
        [visuals.javafx utils]))



(defrecord JfxToolkit []
  Toolkit
  (build* [tk spec] (builder/build spec))
  (show!* [tk vc] (.show vc))
  (hide!* [tk vc] (.hide vc))
  (run-later*
    [tk f]
    (launch-if-necessary)
    (Platform/runLater f)))



(extend-type Node
  VisualComponent
  (compname [vc] (.getId vc))
  (comptype [vc] (-> vc (getp :spec) m/metatype))
  (parent [vc] (.getParent vc))
  (set-error! [vc on] (.setStyle vc (if on "-fx-border-color: red;" "-fx-border-color: white;")))
  (children [vc] (if (instance? MigPane vc) (or (.getChildren vc) []) []))
  (eventsources [vc] (getp vc :eventsources))
  (signals [vc] (getp vc :signals)))


(extend-type Stage
  VisualComponent
  (compname [vc] (-> vc (getp :spec) :name))
  (comptype [vc] (-> vc (getp :spec) m/metatype))
  (parent [vc] (.getOwner vc))
  (set-error! [vc on] on)
  (children [vc] [(-> vc .getScene .getRoot)])
  (eventsources [vc] (getp vc :eventsources))
  (signals [vc] (getp vc :signals)))




