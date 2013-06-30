(ns visuals.javafx.sample
  "Sample program"  
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]
            [reactor.core :as r]))


(v/init-toolkit! (tk/->JfxToolkit))


(def p (f/panel "P" :lygeneral "wrap 2"
                :components [
                (f/textfield "Name")
                (f/textfield "Street")
                (f/textfield "Zipcode")
                (f/textfield "City")
                (f/button "B" :text "OK" :lyhint "skip")]))

(def w (f/window "HelloWorld" :content p))


(def mapping {:name    ["Name" :text]
              :street  ["Street" :text]
              :zipcode ["Zipcode" :text]
              :city    ["City" :text]})

;; To actually see something happen, enter in a REPL (without #_):

#_(def s (v/run-now (-> w v/build v/show!)))

#_(v/to-components! mapping (v/cmap s) {:name "Donald Duck"
                                        :street "Upperstr. 15"
                                        :zipcode "12345"
                                        :city "Duckberg"})
