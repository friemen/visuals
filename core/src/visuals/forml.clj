(ns visuals.forml
  (:use [metam.core]))

(declare default-value)

(defmetamodel forml
  (-> (make-hierarchy)
      (derive ::container ::component)
      (derive ::widget ::component)
      ; concrete component types
      (derive ::button ::widget)
      (derive ::dropdownlist ::labeled)
      (derive ::dropdownlist ::widget)
      (derive ::label ::widget)
      (derive ::panel ::growing)
      (derive ::panel ::container)
      (derive ::textfield ::labeled)
      (derive ::textfield ::widget))
  {::button       {:text [string?]
                   :lyhint [string?]}
   ::dropdownlist {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::label        {:text [string?]
                   :lyhint [string?]}
   ::panel        {:lygeneral [string?]
                   :lycolumns [string?]
                   :lyrows [string?]
                   :lyhint [string?]
                   :components [(type-of? ::component)]}
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

(defmacro ^:private defdefault
  [dispatch-value & forms]
  (let [args ['spec 'tk 'ak]]
    `(defmethod default-value ~dispatch-value
     ~args
     ~@forms)))

(defdefault :default                     nil)
(defdefault [::widget :lyhint]           "")
(defdefault [::growing :lyhint]          "grow")
(defdefault [::panel :lyrows]            "")
(defdefault [::panel :lycolumns]         "")
(defdefault [::labeled :labelyhint]      "")
(defdefault [::labeled :label]           (:name spec))
(defdefault [::button :text]             (:name spec))
(defdefault [::window :title]            (:name spec))
