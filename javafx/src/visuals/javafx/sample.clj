(ns visuals.javafx.sample
  "Sample"
  (:require [visuals.javafx.toolkit :as tk]
            [visuals.core :as v]))

;; Configure to use JavaFX toolkit

(v/init-toolkit! (tk/->JfxToolkit))


;; Try in the REPL

#_(require '[visuals.javafx.sample :as s]
           '[reactor.core :as r]
           '[visuals.core :as v]
           '[visuals.forml :as f])


;; Define and show panel
#_(def vs (v/preview
           (f/panel "Listbox" :lygeneral "flowy"
                    :components
                    [(f/listbox "Items")
                     (f/panel "Actions" :lygeneral "nogrid, ins 0"
                              :components
                              [(f/button "Add Item")
                               (f/button "Remove Item")])])))


;; Add data to the list
#_(-> vs (v/signal "Items" :items) (r/setv! ["Foo" "Bar" "Baz"]))

;; Add mapping from listbox items to ::v/ui-state
#_(-> vs (v/update! ::v/ui-state-mapping (v/mapping :items ["Items" :items]
                                                    :selected ["Items" :selected])))


;; Add action method to :action eventsource of a button
#_(v/set-action! vs (fn [view]
                      (update-in view [::v/ui-state :items] #(conj % (str "NEW " (count %)))))
                 "Add Item"
                 :action)

#_(v/set-action! vs (fn [view]
                      (let [{items :items s :selected} (::v/ui-state view)]
                        (assoc-in view [::v/ui-state :items]
                                  (->> items
                                       (map vector (range))
                                       (remove #((set s) (first %)))
                                       (map second)
                                       vec))))
                 "Remove Item"
                 :action)





