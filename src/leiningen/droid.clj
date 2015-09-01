;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:require clojure.pprint
            [leiningen.droid.aar :refer [extract-aar-dependencies]]
            [leiningen.droid.code-gen :refer [code-gen]])
  (:use [leiningen.core.project :only [set-profiles]]
        [leiningen.core.main :only [abort]]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.clean :only [clean]]
        [leiningen.droid.compile :only [compile]]
        [leiningen.droid
         [classpath :only [init-hooks]]
         [build :only [create-dex
                       crunch-resources package-resources create-apk
                       sign-apk zipalign-apk apk build jar aar]]
         [deploy :only [install run forward-port repl deploy local-repo]]
         [new :only [new init]]
         [test :only [local-test]]
         [utils :only [proj wrong-usage android-parameters sdk-sanity-check
                       dev-build?]]]))

(defn help
  "Shows the list of possible `lein droid` subtasks."
  ([]) ([droid-var]
          (println "lein-droid is a plugin for Clojure/Android development."
                   (subtask-help-for nil droid-var))))

(defn pprint
  "Pretty-prints a representation of the project map."
  [project & keys]
  (if (seq keys)
    (clojure.pprint/pprint (select-keys project (map read-string keys)))
    (clojure.pprint/pprint project))
  (flush))

(declare execute-subtask)

(defn doall
  "Metatask. Performs all Android tasks from compilation to deployment."
  [{{:keys [library]} :android :as project} & device-args]
  (let [build-steps (if library ["build"] ["build" "apk" "deploy"])]
    (doseq [task build-steps]
      (execute-subtask project task device-args))))

(defn ^{:no-project-needed true
        :subtasks [#'new #'init #'code-gen #'compile #'create-dex
                   #'crunch-resources #'package-resources
                   #'create-apk #'sign-apk #'zipalign-apk
                   #'install #'run #'forward-port #'repl
                   #'build #'apk #'deploy #'doall #'help #'local-test
                   #'jar #'pprint]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([project]
     (help #'droid))
  ([project & [cmd & args]]
     (init-hooks)
     (if (#{"new" "help" "init"} cmd)
       (execute-subtask nil cmd args)
       (let [env-project (System/getenv "LEIN_DROID_PROJECT")
             project (if-not (empty? env-project) (proj env-project) project)]
         (cond (= cmd "pprint") (execute-subtask project cmd args)
               project (doto project
                         sdk-sanity-check
                         extract-aar-dependencies
                         (execute-subtask cmd args))
               :else (abort "Subtask" cmd "should be run from the project folder."))))))

(defn execute-subtask
  "Executes a subtask defined by `name` on the given project."
  [project name args]
  (case name
    ;; Standalone tasks
    "new" (if (< (count args) 2)
            (abort (wrong-usage "lein droid new" #'new))
            (apply new args))
    "init" (init (.getAbsolutePath (clojure.java.io/file ".")))
    "code-gen" (code-gen project)
    "compile" (compile project)
    "create-dex" (create-dex project)
    "crunch-resources" (crunch-resources project)
    "package-resources" (package-resources project)
    "create-apk" (create-apk project)
    "sign-apk" (sign-apk project)
    "zipalign-apk" (zipalign-apk project)
    "install" (apply install project args)
    "run" (apply run project args)
    "forward-port" (apply forward-port project args)
    "repl" (repl project)
    "clean" (clean project)
    "local-repo" (local-repo project)

    ;; Test tasks
    "local-test" (apply local-test project args)

    ;; Meta tasks
    "build" (build project)
    "apk" (apk project)
    "deploy" (apply deploy project args)
    "doall" (apply doall project args)
    "jar" (jar project)
    "aar" (aar project)

    ;; Help tasks
    "pprint" (apply pprint project args)
    "help" (help #'droid)

    (println "Subtask is not recognized:" name
             (subtask-help-for nil #'droid))))
