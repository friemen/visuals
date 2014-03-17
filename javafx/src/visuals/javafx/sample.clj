(ns visuals.javafx.sample
  "Sample"
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.core :as v]
            [reactor.core :as r]
            [visuals.forml :as f]))

;; Configure to use JavaFX toolkit

(v/init-toolkit! (tk/->JfxToolkit))


;; Try in the REPL

#_(require '[visuals.javafx.sample :as s]
           '[reactor.core :as r]
           '[visuals.core :as v]
           '[visuals.forml :as f])


;; Shows panel preview, whenever you reeavaluate the spec var
;; the preview is updated accordingly 
#_(def vs (v/preview #'s/spec))

;; Add data to the list
#_(-> vs (v/signal "Items" :items) (r/setv! ["Foo" "Bar" "Baz"]))

;; Add mapping from listbox items to ::v/ui-state
#_(-> vs (v/update! ::v/ui-state-mapping (v/mapping :items ["Items" :items]
                                                    :selected ["Items" :selected]
                                                    :newitem ["New item" :text])))

;; Install event handler
#_(v/install-handler! vs #'s/handler)




;; Specify panel and event handler

(def spec
  (f/panel "Content" :lygeneral "flowy, fill" :lyrows "[|||grow|]"
           :components
           [(f/textfield "New item" :lyhint "growx")
            (f/listbox "Items")
            (f/panel "Actions" :lygeneral "nogrid, ins 0"
                     :components
                     [(f/button "Add Item")
                      (f/button "Remove Items")])]))

(defn handler
  [view evt]
  (condp v/event-matches? evt
    ["Add Item" :action]
    (let [s (get-in view [::v/ui-state :newitem])]
      (update-in view [::v/ui-state :items] #(conj % s)))
    
    ["Remove Items" :action]
    (let [{items :items s :selected} (::v/ui-state view)]
      (assoc-in view [::v/ui-state :items]
                (->> items
                     (map vector (range))
                     (remove #((set s) (first %)))
                     (map second)
                     vec)))
    view))


;; You can reevaluate spec or handler.
;; The preview window immediately reflects the changes.





