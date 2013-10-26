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
    (.setAll olist (cast java.util.Collection value))
    value))


(defn for-props
  "Returns a map property name to signal or eventsource (depends on the factory-fn) for
   all propnames and the given visual component."
  [component factory-fn propnames]
  (->> propnames
       (map (partial factory-fn component))
       (map #(vector (react/role %) %))
       #_(map #(vector (keyword %) (factory-fn component %)))
       (into {})))

;;TODO refactor
(defn prop-signal
  "Creates a signal for a property of a component. If the propname ends with [] a
   collection instead of a property is assumed."
  [component propname]
  (if (.endsWith propname "[]")
    (let [propname-without-brackets (.substring propname 0 (- (count propname) 2))
          getter (str "get" (first-upper propname-without-brackets))
          olist (invoke component getter)]
      (ObservableListBasedSignal. (keyword (first-lower propname-without-brackets)) olist (atom {})))
    (let [prop (invoke component (str (first-lower propname) "Property"))] 
      (PropertyBasedSignal. (keyword (first-lower propname)) prop (atom {})))))


(defn comp-eventsource
  "Creates an eventsource that is filled by an event handler implementation. The
   event handler is registered with the given visual component."
  [component propname]
  (if-let [eh (invoke component (str "get" (first-upper propname)))]
    (if (satisfies? reactor.core.EventSource eh)
      eh
      (throw (IllegalStateException. (str "Event handler already bound for " propname ": " eh))))
    (let [newes (react/eventsource (keyword (first-lower propname)) v/ui-thread)
          eh (reify javafx.event.EventHandler
               (handle [_ evt]
                 (dump "event raised" (react/raise-event! newes evt))))]
      (invoke component (str "set" (first-upper propname)) [javafx.event.EventHandler] [eh])
      newes)))


