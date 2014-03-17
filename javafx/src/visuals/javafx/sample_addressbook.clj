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



;; Description of a UI panel and a window

(def master-panel
  (f/panel "Master" :lygeneral "flowy"
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


;; Details form

(def details-panel
  (f/panel "Details" :lygeneral "wrap 2"
           :components
           [(f/textfield "Name")
            (f/textfield "Street")
            (f/dropdownlist "Zipcode")
            (f/textfield "City")
            (f/textfield "Birthday")
            (f/panel "Actions" :lygeneral "nogrid, ins 0"
                     :components
                     [(f/button "Ok" :text "Save" :icon "tick")
                      (f/button "Cancel" :icon "cross")])]))

(defn details-handler
  [view evt]
  (condp v/event-matches? evt
    ["Ok" :action]
    (-> view
        (assoc-in [::v/domain-data :city] "DUCKBERG")
        (v/pass-to "Addressbook")
        v/close)
    ["Cancel" :action]
    (v/close view)
    view))


(defn details-view
  [address]
  (v/view (f/window "Details"
                    :content details-panel
                    :modality :window
                    :owner "Addressbook")
          ::v/domain-data-mapping
          (v/mapping :name    ["Name" :text]
                     :street  ["Street" :text]
                     :zipcode ["Zipcode" :value]
                     :city    ["City" :text]
                     :age     ["Birthday" :text]  pf/format-date pf/parse-date)
          ::v/domain-data
          address
          ::v/ui-state-mapping
          (v/mapping :zipcodes ["Zipcode" :items])
          ::v/ui-state
          {:zipcodes ["12345" "53113" "4711"]}
          ::v/validation-rule-set
          (e/rule-set :name c/required (c/min-length 3)
                      :age c/not-blank? c/is-date)
          ::v/handler-fn #'details-handler))


;; test data

(def addresses
  (atom [{:name "Donald Duck"
          :street "Upperstr. 15"
          :zipcode "4711"
          :city "Duckberg"}
         {:name "Mickey Mouse"
          :street "Downstr. 42"
          :zipcode "4711"
          :city "Duckberg"}]))


(defn master-handler
  [view evt]
  (condp v/event-matches? evt
    ["Edit" :action]
    (if-let [sel-index (-> view ::v/ui-state :selected first)]
      (let [address (-> view
                        ::v/domain-data :addresses
                        (nth sel-index)
                        (assoc :id sel-index))]
        (v/create view (details-view address))))
    ["Add" :action]
    (v/create view (details-view {}))
    ["Details"]
    (let [address (-> evt ::v/payload ::v/domain-data)]
      (assoc-in view [::v/domain-data :addresses]
                (if-let [index (:id address)]
                  (swap! addresses assoc index address)
                  (swap! addresses conj address))))
    view))


(defn start-master!
  []
  (-> (f/window "Addressbook" :content master-panel)
      v/view-signal
      (v/update! ::v/domain-data-mapping (v/mapping :addresses ["Addresses" :items])
                 ::v/domain-data {:addresses @addresses}
                 ::v/ui-state-mapping (v/mapping :selected ["Addresses" :selected])
                 ::v/handler-fn #'master-handler)
      v/start!
      v/show!))


