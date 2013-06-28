(ns visuals.javafx.utils
  "JavaFX utilities"
  (:require [visuals.javafx.application])
  (:import [javafx.application Platform Application]
           [javafx.scene Scene Node]
           [javafx.stage Stage]))


(defonce ^:private app-starter-thread (atom nil))
(defonce ^:private force-toolkit-init
  (do 
    (Platform/setImplicitExit false)
    (javafx.embed.swing.JFXPanel.)))


(compile 'visuals.javafx.application)

(defn launch-if-necessary
  []
  (when-not @app-starter-thread
    (reset! app-starter-thread (Thread. #(javafx.application.Application/launch
                                        visuals.javafx.application
                                        (into-array String []))))
    (.start @app-starter-thread))
  @visuals.javafx.application/root-stage)


(defn root-window
  []
  (launch-if-necessary))



;; Component Property access

(defmulti prop-map class)

(defmethod prop-map Node
  [component]
  (.getProperties component))

(defmethod prop-map Scene
  [scene]
  (if-let [m (-> scene .getRoot .getProperties :scene-map)]
    m
    (let [m (java.util.HashMap.)]
      (-> scene .getRoot .getProperties (.put :scene-map m))
      m)))


(defmethod prop-map Stage
  [stage]
  (if-let [m (-> stage .getScene .getRoot .getProperties :stage-map)]
    m
    (let [m (java.util.HashMap.)]
      (-> stage .getScene .getRoot .getProperties (.put :stage-map m))
      m)))


(defn getp
  [component key]
  (-> component prop-map key))


(defn putp!
  [component key value]
  (-> component prop-map (.put key value)))

