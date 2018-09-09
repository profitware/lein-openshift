(ns leiningen.openshift
  (:require [clojure.java.io :as io]
            [leiningen.jar :as jar]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.ring.util :as util]
            [leiningen.ring.uberjar :as uberjar]))


(defn- exec [& args]
  (apply main/warn "Exec: oc" args)
  (apply eval/sh "oc" args))


(defn- create-uberjar [project]
  (uberjar/uberjar project))


(defn- openshift-login [config]
  (let [exit-code (or (and (zero? (exec "whoami"))
                           0)
                      (let [token (:token config)]
                        (if-let [server (:server config)]
                          (exec "login" server (str "--token=" token))
                          (exec "login"))))]
    (zero? exit-code)))


(defn- openshift-new-project [namespace]
  (or (zero? (exec "get"
                   "projects"
                   namespace))
      (zero? (exec "new-project"
                   namespace))))


(defn- openshift-import-image [namespace]
  (zero? (exec "import-image"
               "my-redhat-openjdk-18/openjdk18-openshift"
               "--from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift"
               "--confirm"
               (str "--namespace=" namespace))))


(defn- openshift-check-uberjar [uberjar]
  (or (.exists (io/as-file uberjar))
      (do (main/warn (str "Uberjar " uberjar
                          " doesn't exist. Create it using "
                          "\"lein uberjar\" or \"lein ring uberjar\"."))
          false)))


(defn- openshift-new-app [namespace app environment]
  (or (zero? (exec "get"
                   "deploymentconfigs"
                   app
                   (str "--namespace=" namespace)))
      (zero? (apply exec
                    (concat (list "new-app"
                                  app)
                            (when environment
                              (list "-e"))
                            (interpose "-e" (map #(let [[k v] %]
                                                    (if (empty? v)
                                                      (do (print (str "Enter value for key " k " : "))
                                                          (flush)
                                                          (str k "=" (read-line)))
                                                      (str k "=" v)))
                                                 environment))
                            (list (str "--namespace=" namespace)))))))


(defn- openshift-new-build [namespace app]
  (or (zero? (exec "get"
                   "buildconfigs"
                   app
                   (str "--namespace=" namespace)))
      (zero? (exec "new-build"
                   "--binary=true"
                   "-i=openjdk18-openshift:latest"
                   (str "--name=" app)
                   (str "--namespace=" namespace)))))


(defn- openshift-start-build [namespace app uberjar]
  (zero? (exec "start-build"
               app
               (str "--from-file=" uberjar)
               (str "--follow")
               (str "--namespace=" namespace))))


(defn- openshift-patch-recreate [namespace app recreate]
  (when recreate
    (exec "patch"
          (str "dc/" app)
          "--patch"
          "{\"spec\":{\"strategy\":{\"type\":\"Recreate\"}}}"
          (str "--namespace=" namespace)))
  true)


(defn- openshift-expose [namespace app domains]
  (if (sequential? domains)
    (do (zero? (exec "delete"
                     "routes"
                     "-l"
                     (str "app=" app)
                     (str "--namespace=" namespace)))
        (every? zero?
                (map #(exec "expose"
                            (str "svc/" app)
                            (str "--name=" %)
                            (str "--hostname=" %)
                            (str "--namespace=" namespace))
                     domains)))
    true))


(defn openshift
  "Builds and deploys openshift applications.
   Commands:
     'release' builds and releases your openshift application"
  [project & args]

  (let [command (keyword (first args))]

    (let [config (:openshift project)
          recreate (:recreate config)
          environment (or (:env config)
                          (:environment config))
          domains (:domains config)
          app* (or (second args)
                   (:app config)
                   (str (:name project)))
          splitted-app (clojure.string/split app* #"/")
          app (if-let [app-name (second splitted-app)]
                app-name
                app*)
          namespace (or (when-let [namespace (:namespace config)]
                          (str namespace))
                        (if (= (first splitted-app) app)
                          app
                          (second splitted-app)))
          uberjar (jar/get-classified-jar-filename (-> project
                                                       (util/unmerge-profiles [:default])
                                                       (util/merge-profiles [:uberjar]))
                                                   :standalone)]
      (case command
        :release (do (main/info "Releasing Openshift application:" app)
                     (if (and (openshift-login config)
                              (openshift-new-project namespace)
                              (openshift-import-image namespace)
                              (create-uberjar project)
                              (openshift-check-uberjar uberjar)
                              (openshift-new-build namespace app)
                              (openshift-start-build namespace app uberjar)
                              (openshift-new-app namespace app environment)
                              (openshift-patch-recreate namespace app recreate)
                              (openshift-expose namespace app domains))
                       (do (main/info "Openshift application released.")
                           (main/exit 0))
                       (do (main/warn "Openshift application could not be released.")
                           (main/exit 1))))
        (do (let [exit-code (apply exec args)]
              (main/exit exit-code)))))))
