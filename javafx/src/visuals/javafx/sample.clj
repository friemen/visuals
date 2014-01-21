(ns visuals.javafx.sample
  "Sample program"  
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]
            [visuals.parsform :as pf]
            [clj-time.core :as t]
            [reactor.core :as r]
            [examine.core :as e]
            [examine.constraints :as c]))


;; Try in the REPL

#_(require '[visuals.javafx.sample :as s]
           '[visuals.core :as v]
           '[reactor.core :as r]
           '[visuals.forml :as f])


;; Define and show panel
#_(def vs (v/preview
           (f/panel "Listbox" :lygeneral "flowy" :components [
               (f/listbox "Items")
               (f/button "Add Item")])))

;; Add mapping from listbox items to ::v/ui-state
#_(-> vs (v/update! ::v/ui-state-mapping (v/mapping :items ["Items" :items])))


;; Add action method to :onAction eventsource of a button
#_(v/set-action! vs (fn [view]
                      (update-in view [::v/ui-state :items] #(conj % "NEW")))
                 "Add Item"
                 :action)


;; First load this namespace into a REPL

;; Define and start view as defined below.
#_(def view-sig (v/run-now (s/start-view!)))
;; The var view-sig holds the immutable map that represents a View.


;; Get value from UI
#_(-> view-sig (v/signal "City" :text) r/getv)


;; Set value into UI
#_(-> view-sig (v/signal "City" :text) (r/setv! "BAZ"))


;; Get event source and trigger an event
#_(-> view-sig (v/eventsource "Ok" :action) (r/raise-event! nil))




;; Configure to use JavaFX toolkit

(v/init-toolkit! (tk/->JfxToolkit))


;; Description of a UI panel and a window

(def p (f/panel "P" :lygeneral "wrap 2" :components [
            (f/textfield "Name")
            (f/textfield "Street")
            (f/dropdownlist "Zipcode")
            (f/textfield "City")
            (f/textfield "Birthday")
            (f/button "Ok" :text "OK" :lyhint "skip")]))

(def w (f/window "HelloWorld" :content p))


;; Data mapping rules between domain data and visual component properties

(def domain-mapping
  (v/mapping :name    ["Name" :text]
             :street  ["Street" :text]
             :zipcode ["Zipcode" :value]
             :city    ["City" :text]
             :age     ["Birthday" :text]  pf/format-date pf/parse-date))

(def ui-mapping
  (v/mapping :zipcodes ["Zipcode" :items]))

;; Validation rules for domain data

(def validation-rules
  (e/rule-set :name c/required (c/min-length 3)
              :age c/not-blank? c/is-date))


;; Action functions

(defn ^{:action ["Ok" :action]} ok
  [view]
  (println "OK action with domain data" (::v/domain-data view))
  (assoc-in view [::v/domain-data :city] "DUCKBERG"))


;; Take window, create view and start it

(defn start-view!
  []
  (-> w
      v/view-signal
      (v/update! ::v/domain-data-mapping domain-mapping
                 ::v/domain-data {:name "Donald Duck"
                                  :street "Upperstr. 15"
                                  :zipcode "4711"
                                  :city "Duckberg"}
                 ::v/ui-state-mapping ui-mapping
                 ::v/ui-state {:zipcodes ["12345" "53113" "4711"]}
                 ::v/validation-rule-set validation-rules 
                 ::v/action-fns (v/action-fns 'visuals.javafx.sample))
      v/start!
      v/show!))
