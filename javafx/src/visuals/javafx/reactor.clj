(ns visuals.javafx.reactor
  "JavaFX reactive implementations"
  (:require [reactor.core :as react]
            [visuals.core :as v]
            [visuals.utils :refer :all])
  (:import reactor.core.EventSource))


(defrecord PropertyBasedSignal [propname property clmap-atom updated-atom] 
  reactor.core/Reactive
  (subscribe
    [sig follower f]
    (let [l (reify javafx.beans.value.ChangeListener
              (changed [_ subject old new] (f [old new])))]
      (swap! clmap-atom #(assoc % f [l follower]))
      (.addListener property l)))
  (unsubscribe
    [sig f]
    (when-let [[l _] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener property l)))
  (followers
    [sig]
    (->> clmap-atom deref vals (map second)))
  (role
    [sig]
    propname)
  (publish!
    [sig value]
    (.setValue property value)
    (reset! updated-atom (react/now))
    value)
  reactor.core/Signal
  (getv
    [sig]
    (.getValue property))
  (last-updated
    [sig]
    @updated-atom))


(defrecord ObservableListBasedSignal [propname olist clmap-atom updated-atom]
  reactor.core/Reactive
  (subscribe
    [sig follower f]
    (let [l (reify javafx.collections.ListChangeListener
              (onChanged [_ evt] (f [nil (.getList evt)])))]
      (swap! clmap-atom #(assoc % f [l follower]))
      (.addListener olist l)))
  (unsubscribe
    [sig f]
    (when-let [[l _] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener olist l)))
  (followers
    [sig]
    (->> clmap-atom deref vals (map second)))
  (role
    [sig]
    propname)
  (publish!
    [sig value]
    (v/run-later (do (reset! updated-atom (react/now))
                     (into-list! olist value)))
    value)
  reactor.core/Signal
  (getv
    [sig]
    (into [] olist))
  (last-updated
    [sig]
    @updated-atom))


(defrecord SelectionModelBasedSignal [propname selmodel clmap-atom updated-atom]
  reactor.core/Reactive
  (subscribe
    [sig follower f]
    (let [l (reify javafx.collections.ListChangeListener
              (onChanged [_ evt] (f [nil (.getList evt)])))]
      (swap! clmap-atom #(assoc % f [l follower]))
      (.addListener (.getSelectedIndices selmodel) l)))
  (unsubscribe
    [sig f]
    (when-let [[l _] (get @clmap-atom f)]
      (swap! clmap-atom #(dissoc % f))
      (.removeListener (.getSelectedIndices selmodel) l)))
  (followers
    [sig]
    (->> clmap-atom deref vals (map second)))
  (role
    [sig]
    propname)
  (publish!
    [sig value]
    (v/run-later (do (reset! updated-atom (react/now))
                     (if (seq value)
                       (.selectIndices selmodel (int (first value)) (int-array (rest value)))
                       (.clearSelection selmodel))))
    value)
  reactor.core/Signal
  (getv
    [sig]
    (into [] (.getSelectedIndices selmodel)))
  (last-updated
    [sig]
    @updated-atom))


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
      (PropertyBasedSignal. key prop (atom {}) (atom 0))))


(defn list-signal
  "Creates a signal for an observable list."
  [{component :component key :key propname :propname}]
  (let [getter (str "get" (first-upper propname))
        olist (invoke component getter)]
    (ObservableListBasedSignal. key olist (atom {}) (atom 0))))


(defn selection-signal
  "Creates a signal for a selection model of a component with selectable items."
  [{component :component key :key propname :propname}]
  (SelectionModelBasedSignal. (keyword (first-lower propname))
                              (.getSelectionModel component)
                              (atom {})
                              (atom 0)))


(defn comp-eventsource
  "Creates an eventsource that is filled by an event handler implementation. The
   event handler is registered with the given visual component."
  [{component :component key :key propname :propname}]
  (let [newes (react/eventsource key v/ui-thread)
        eh (reify javafx.event.EventHandler
             (handle [_ evt]
               (dump "event raised" (react/raise-event! newes evt))))]
    (invoke component (str "set" (first-upper propname)) [javafx.event.EventHandler] [eh])
    newes))


