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


;; To actually see something happen, enter in a REPL (without #_):

#_(use 'visuals.javafx.sample)
#_(def v (visuals.core/run-now (start-view!)))


;; Configure to use JavaFX toolkit

(v/init-toolkit! (tk/->JfxToolkit))


;; Description of a UI panel and a window

(def p (f/panel "P" :lygeneral "wrap 2" :components [
            (f/textfield "Name")
            (f/textfield "Street")
            (f/textfield "Zipcode")
            (f/textfield "City")
            (f/textfield "Birthday")
            (f/button "Ok" :text "OK" :lyhint "skip")]))

(def w (f/window "HelloWorld" :content p))


;; Data mapping rules between domain data and visual component properties

(def domain-mapping
  (v/mapping :name    ["Name" :text]
             :street  ["Street" :text]
             :zipcode ["Zipcode" :text]
             :city    ["City" :text]
             :age     ["Birthday" :text]  pf/format-date pf/parse-date))



;; Validation rules for domain data

(def validation-rules
  (e/rule-set :name c/required (c/min-length 3)
              :age c/not-blank? c/is-date))


;; Action functions

(defn ^{:action ["Ok" :OnAction]} ok
  [view]
  (assoc-in view [::v/domain-data :city] "DUCKBERG"))


;; Take window, create view and start it

(defn start-view!
  []
  (-> w
      v/view-signal
      (v/update! ::v/domain-data-mapping domain-mapping
                 ::v/domain-data {:name "Donald Duck"
                                  :street "Upperstr. 15"
                                  :zipcode "12345"
                                  :city "Duckberg"}
                 ::v/validation-rule-set validation-rules 
                 ::v/action-fns (v/action-fns 'visuals.javafx.sample))
      v/start!
      v/show!))






