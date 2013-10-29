(ns visuals.forml
  (:refer-clojure :exclude [list])
  (:require [metam.core :refer :all]
            [visuals.utils :refer [first-lower]]))

(declare default-value)

(defmetamodel forml
  (-> (make-hierarchy)
      (derive ::container ::component)
      (derive ::widget ::component)
      (derive ::column ::component)
      ; concrete component types
      (derive ::button ::widget)
      (derive ::buttongroup ::container)
      (derive ::buttongroup ::labeled)
      (derive ::checkbox ::widget)
      (derive ::checkbox ::labeled)
      (derive ::dropdownlist ::labeled)
      (derive ::dropdownlist ::widget)
      (derive ::label ::widget)
      (derive ::list ::labeled)
      (derive ::list ::widget)
      (derive ::list ::growing)
      (derive ::panel ::growing)
      (derive ::panel ::container)
      (derive ::radio ::widget)
      (derive ::table ::labeled)
      (derive ::table ::widget)
      (derive ::table ::growing)
      (derive ::textfield ::labeled)
      (derive ::textfield ::widget))
  {::button       {:text [string?]
                   :lyhint [string?]
                   :icon [string?]}
   ::buttongroup  {:label [string?]
                   :labelyhint [string?]
                   :orientation [(value-of? :vertical :horizontal)]
                   :buttons [#(or (string? %) ((type-of? ::radio) %))]}
   ::checkbox     {:label [string?]
                   :labelyhint [string?]
                   :text [string?]}
   ::column       {:title [string?]
                   :key [keyword?]}
   ::dropdownlist {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::label        {:text [string?]
                   :lyhint [string?]
                   :icon [string?]}
   ::list         {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::panel        {:lygeneral [string?]
                   :lycolumns [string?]
                   :lyrows [string?]
                   :lyhint [string?]
                   :components [(type-of? ::component)]}
   ::radio        {:text [string?]
                   :value [(complement nil?)]}
   ::table        {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]
                   :columns [(type-of? ::column)]}
   ::textfield    {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::window       {:title [string?]
                   :content [(type-of? ::container)]}}
  #'default-value)


(defmulti default-value
  (fn [spec type-keyword attr-keyword]
    [type-keyword attr-keyword])
  :hierarchy #'forml-hierarchy)

(prefer-method default-value [::growing :lyhint] [::widget :lyhint])

(defmacro ^:private defdefault
  [dispatch-value & forms]
  (let [args ['spec 'tk 'ak]]
    `(defmethod default-value ~dispatch-value
     ~args
     ~@forms)))

(defdefault :default                     nil)
(defdefault [::button :text]             (:name spec))
(defdefault [::buttongroup :orientation] :horizontal)
(defdefault [::column :title]            (:name spec))
(defdefault [::column :key]              (-> spec :name first-lower keyword))
(defdefault [::checkbox :text]           "")
(defdefault [::checkbox :label]          (if (:text spec) nil (:name spec)))
(defdefault [::growing :lyhint]          "grow")
(defdefault [::labeled :labelyhint]      "")
(defdefault [::labeled :label]           (:name spec))
(defdefault [::panel :lyrows]            "")
(defdefault [::panel :lycolumns]         "")
(defdefault [::radio :text]              (:name spec))
(defdefault [::radio :value]             (:name spec))
(defdefault [::widget :lyhint]           "")
(defdefault [::window :title]            (:name spec))
