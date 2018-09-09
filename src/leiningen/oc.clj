(ns leiningen.oc
  (:require [leiningen.openshift]))


(defn oc [project & args]
  (apply leiningen.openshift/openshift (concat (list project)
                                               args)))
