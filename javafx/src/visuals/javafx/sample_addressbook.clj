(ns visuals.javafx.sample-addressbook
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]
            [visuals.parsform :as pf]
            [clj-time.core :as t]
            [reactor.core :as r]
            [examine.core :as e]
            [examine.constraints :as c]))

;; Configure to use JavaFX toolkit

(defonce toolkit (v/init-toolkit! (tk/->JfxToolkit)))



;; Description of a UI panel and a window

(def master-panel (f/panel "Master" :lygeneral "flowy"
                           :components
                           [(f/table "Addresses"
                                     :columns
                                     [(f/column "Name")
                                      (f/column "Street")
                                      (f/column "Zipcode")
                                      (f/column "City")])
                            (f/panel "Actions" :lygeneral "nogrid, ins 0"
                                     :components
                                     [(f/button "Add")
                                      (f/button "Edit")
                                      (f/button "Remove")])]))


(def details-panel (f/panel "Details" :lygeneral "wrap 2"
                            :components
                            [(f/textfield "Name")
                             (f/textfield "Street")
                             (f/dropdownlist "Zipcode")
                             (f/textfield "City")
                             (f/textfield "Birthday")
                             (f/panel "Actions" :lygeneral "nogrid, ins 0"
                                      :components
                                      [(f/button "Ok" :text "OK" :icon "tick")
                                       (f/button "Cancel" :icon "cross")])]))



;; Action functions

(defn ^{:events ["Ok" :action]} ok
  [view evt]
  (println "OK action with domain data" (::v/domain-data view))
  (assoc-in view [::v/domain-data :city] "DUCKBERG"))


;; Take window, create view and start it

(defn start-details!
  [owner address]
  (println "START DETAILS")
  (let [domain-mapping
        (v/mapping :name    ["Name" :text]
                   :street  ["Street" :text]
                   :zipcode ["Zipcode" :value]
                   :city    ["City" :text]
                   :age     ["Birthday" :text]  pf/format-date pf/parse-date)
        ui-mapping
        (v/mapping :zipcodes ["Zipcode" :items])
        validation-rules
        (e/rule-set :name c/required (c/min-length 3)
                    :age c/not-blank? c/is-date)]
    (-> (f/window "Details" :content details-panel)
        v/view-signal
        (v/update! ::v/domain-data-mapping domain-mapping
                   ::v/domain-data address
                   ::v/ui-state-mapping ui-mapping
                   ::v/ui-state {:zipcodes ["12345" "53113" "4711"]}
                   ::v/validation-rule-set validation-rules 
                   ::v/handler-fns {["Ok" :action] ok})
        v/start!
        v/show!)))


(def addresses (atom [{:name "Donald Duck"
                       :street "Upperstr. 15"
                       :zipcode "4711"
                       :city "Duckberg"}
                      {:name "Mickey Mouse"
                       :street "Downstr. 42"
                       :zipcode "4711"
                       :city "Duckberg"}]))

(defn start-master!
  []
  (-> (f/window "Addressbook" :content master-panel)
      v/view-signal
      (v/update! ::v/domain-data-mapping (v/mapping :addresses ["Addresses" :items])
                 ::v/domain-data {:addresses @addresses}
                 ::v/ui-state-mapping (v/mapping :selected ["Addresses" :selected]))
      (v/start! (fn [view evt]
                  (condp v/event-matches? evt
                    ["Edit" :action]
                    (if-let [s (-> view ::v/ui-state :selected first)]
                        (start-details! (::v/vc view)
                                         (-> view ::v/domain-data :addresses (nth s))))
                    view)
                  view))
      v/show!))


;; In the REPL switch to this namespace and load it

;; Define and start view as defined below.
#_(def master (v/run-now (start-master!)))
;; The var view-sig holds the immutable map that represents a View.


;; Get value from UI
#_(-> details (v/signal "City" :text) r/getv)


;; Set value into UI
#_(-> details (v/signal "City" :text) (r/setv! "BAZ"))


;; Get event source and trigger an event
#_(-> details (v/eventsource "Ok" :action) (r/raise-event! nil))

