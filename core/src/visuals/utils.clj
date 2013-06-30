(ns visuals.utils
  "Abstractions and common, toolkit-independent, internal functions")

(defprotocol Toolkit
  (build* [tk spec])
  (show!* [tk vc])
  (hide!* [tk vc])
  (run-later* [tk f]))


(defn invoke
  ([instance methodname]
     (invoke instance methodname nil []))
  ([instance methodname parameter-types parameter-values]
     (-> instance
         class
         (.getMethod methodname (into-array java.lang.Class parameter-types))
         (.invoke instance (object-array parameter-values)))))

