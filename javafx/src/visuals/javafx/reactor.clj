(ns visuals.javafx.reactor
  "JavaFX reactive implementations"
  (:require [reactor.core :as react])
  (:use [visuals.utils]))

(defrecord PropertyBasedSignal [propname property clmap-atom]
  
  reactor.core/Reactive
  
  (subscribe
    [sig f followers]
    (let [l (reify javafx.beans.value.ChangeListener
                    (changed [_ subject old new] (f new)))]
      (swap! clmap-atom #(assoc % f [l followers]))
      (.addListener property l)))
  
  (unsubscribe
    [sig f]
    (when-let [[l followers] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener property l)))
  
  (followers
    [sig]
    (->> clmap-atom deref vals second (apply concat)))
  
  (role
    [sig]
    propname)
  
  reactor.core/Signal
  
  (getv
    [sig]
    (.getValue property))
  
  (setv!
    [sig value]
    (.setValue property value) value))


(defn for-props
  [component factory-fn propnames]
  (->> propnames
       (map #(vector (keyword %) (factory-fn component %)))
       (into {})))


(defn prop-signal
  [component propname]
  (let [prop (invoke component (str propname "Property"))] 
    (PropertyBasedSignal. (keyword propname) prop (atom {}))))


(defn comp-eventsource
  [component propname]
  (if-let [eh (invoke component (str "get" propname))]
    (if (satisfies? reactor.core.EventSource eh)
      eh
      (throw (IllegalStateException. (str "Event handler already bound for " propname ": " eh))))
    (let [newes (react/eventsource (keyword propname))
          eh (reify javafx.event.EventHandler
               (handle [_ evt]
                 (react/raise-event! newes evt)))]
      (invoke component (str "set" propname) [javafx.event.EventHandler] [eh])
      newes)))

