(ns visuals.javafx.reactor
  "JavaFX reactive implementations"
  (:require [reactor.core :as react]
            [visuals.core :as v]
            [visuals.utils :refer :all]))

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
    (.setValue property value)
    value))


(defrecord ObservableListBasedSignal [propname olist clmap-atom]
  reactor.core/Reactive
  (subscribe
    [sig f followers]
    (let [l (reify javafx.collections.ListChangeListener
              (onChanged [_ evt] (f (.getList evt))))]
      (swap! clmap-atom #(assoc % f [l followers]))
      (.addListener olist l)))
  (unsubscribe
    [sig f]
    (when-let [[l followers] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener olist l)))
  (followers
    [sig]
    (->> clmap-atom deref vals second (apply concat)))
  (role
    [sig]
    propname)
  reactor.core/Signal
  (getv
    [sig]
    (into [] olist))
  (setv!
    [sig value]
    (v/run-later (into-list! olist value))
    value))


(defrecord SelectionModelBasedSignal [propname selmodel clmap-atom]
  reactor.core/Reactive
  (subscribe
    [sig f followers]
    (let [l (reify javafx.collections.ListChangeListener
              (onChanged [_ evt] (f (.getList evt))))]
      (swap! clmap-atom #(assoc % f [l followers]))
      (.addListener (.getSelectedIndices selmodel) l)))
  (unsubscribe
    [sig f]
    (when-let [[l followers] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener (.getSelectedIndices selmodel) l)))
  (followers
    [sig]
    (->> clmap-atom deref vals second (apply concat)))
  (role
    [sig]
    propname)
  reactor.core/Signal
  (getv
    [sig]
    (into [] (.getSelectedIndices selmodel)))
  (setv!
    [sig value]
    (v/run-later (.selectIndices selmodel (int (first value)) (int-array (rest value))))
    value))




;; Utilities for uniform creation of signals and event sources

(defn binding-spec
  "Creates a binding spec map from the given params."
  [component factory-fn key propname]
  {:component component
   :factory-fn factory-fn
   :key key
   :propname propname})


(defn binding-specs
  "Creates a seq of binding specs from the seq of property names.
   The key is derived from the property name."
  [component factory-fn propnames]
  (->> propnames
       (map #(binding-spec component factory-fn (keyword %) %))))


(defn make-reactive
  "Creates a pair [key reactive] from a binding-spec.
   A binding spec is a map with keys :component :factory-fn, :key and :propname."
  [binding-spec]
  (vector (:key binding-spec)
                  ((:factory-fn binding-spec) binding-spec)))


(defn make-reactive-map
  "Creates a map of reactives from pairs of keys and property names."
  [binding-specs]
  (->> binding-specs
       (map make-reactive)
       (into {})))


;; Factory functions for event sources and signals


(defn prop-signal
  "Creates a signal for a property of a component."
  [{component :component key :key propname :propname}]
  (let [prop (invoke component (str (first-lower propname) "Property"))] 
      (PropertyBasedSignal. key prop (atom {}))))


(defn list-signal
  "Creates a signal for an observable list."
  [{component :component key :key propname :propname}]
  (let [getter (str "get" (first-upper propname))
        olist (invoke component getter)]
    (ObservableListBasedSignal. key olist (atom {}))))


(defn selection-signal
  "Creates a signal for a selection model of a component with selectable items."
  [{component :component key :key propname :propname}]
  (SelectionModelBasedSignal. (keyword (first-lower propname))
                              (.getSelectionModel component)
                              (atom {})))


(defn comp-eventsource
  "Creates an eventsource that is filled by an event handler implementation. The
   event handler is registered with the given visual component."
  [{component :component key :key propname :propname}]
  (if-let [eh (invoke component (str "get" (first-upper propname)))]
    (if (satisfies? reactor.core.EventSource eh)
      eh
      (throw (IllegalStateException. (str "Event handler already bound for " propname ": " eh))))
    (let [newes (react/eventsource key v/ui-thread)
          eh (reify javafx.event.EventHandler
               (handle [_ evt]
                 (dump "event raised" (react/raise-event! newes evt))))]
      (invoke component (str "set" (first-upper propname)) [javafx.event.EventHandler] [eh])
      newes)))


