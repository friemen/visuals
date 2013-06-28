(ns visuals.javafx.sample
  "Sample program"  
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.forml :as f]
            [visuals.core :as v]))


(v/init-toolkit! (tk/->JfxToolkit))


(def p (f/panel "P" :lygeneral "wrap 2"
                :components [
                (f/textfield "Name")
                (f/button "B" :text "Bar" :lyhint "skip")]))

(def w (f/window "HelloWorld" :content p))


; (def s (v/run-now (-> w v/build v/show!)))



