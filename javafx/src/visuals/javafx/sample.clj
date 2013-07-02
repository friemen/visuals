(ns visuals.javafx.sample
  "Sample program"  
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]
            [reactor.core :as r]))


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
  (let [v (v/view w
                  mapping
                  {}
                  (v/action-fns 'visuals.javafx.sample)
                  {:name "Donald Duck"
                   :street "Upperstr. 15"
                   :zipcode "12345"
                   :city "Duckberg"}
                  {})]
    (v/show! v)))


;; To actually see something happen, enter in a REPL (without #_):

#_(def s (v/run-now (start-view!)))



