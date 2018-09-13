(ns leiningen.oc
  (:require [leiningen.openshift]))


(defn oc
  "Builds and deploys openshift applications (shorthand).
   Commands:
     'release' builds and releases your openshift application"
  [project & args]
  (apply leiningen.openshift/openshift (concat (list project)
                                               args)))
