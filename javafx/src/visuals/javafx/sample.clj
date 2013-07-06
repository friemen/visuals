(ns visuals.javafx.sample
  "Sample program"  
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]
            [reactor.core :as r]
            [examine.core :as e]
            [examine.constraints :as c]))


(v/init-toolkit! (tk/->JfxToolkit))


(def p (f/panel "P" :lygeneral "wrap 2" :components [
            (f/textfield "Name")
            (f/textfield "Street")
            (f/textfield "Zipcode")
            (f/textfield "City")
            (f/button "Ok" :text "OK" :lyhint "skip")]))

(def w (f/window "HelloWorld" :content p))


(def mapping {:name    ["Name" :text]
              :street  ["Street" :text]
              :zipcode ["Zipcode" :text]
              :city    ["City" :text]})


(defn ^{:action ["Ok" :OnAction]} ok
  [view]
  (assoc-in view [::v/domain-data :city] "DUCKBERG"))


(defn start-view!
  []
  (-> w
      v/view-signal
      (v/update! ::v/domain-data-mapping mapping
                 ::v/domain-data {:name "Donald Duck"
                                  :street "Upperstr. 15"
                                  :zipcode "12345"
                                  :city "Duckberg"}
                 ::v/validation-rule-set (e/rule-set :name c/required (c/min-length 3)) 
                 ::v/action-fns (v/action-fns 'visuals.javafx.sample))
      v/start!
      v/show!))


;; TODO
;; add validation display

;; To actually see something happen, enter in a REPL (without #_):

#_(def v (v/run-now (start-view!)))



