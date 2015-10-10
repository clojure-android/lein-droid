;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:require clojure.pprint
            [clojure.java.io :as io]
            [leiningen.droid.aar :refer [extract-aar-dependencies]]
            [leiningen.droid.code-gen :refer [code-gen generate-resource-code
                                              generate-build-constants]]
            [leiningen.droid.inc-build :as ib])
  (:use [leiningen.core.project :only [set-profiles]]
        [leiningen.core.main :only [info debug abort]]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.clean :only [clean]]
        [leiningen.droid.compile :only [compile]]
        [leiningen.droid
         [compile :only [compile]]
         [classpath :only [init-hooks]]
         [manifest :only [generate-manifest]]
         [build :only [create-dex
                       crunch-resources package-resources create-apk
                       sign-apk zipalign-apk apk build jar aar]]
         [deploy :only [install run forward-port repl deploy local-repo]]
         [new :only [new init]]
         [test :only [local-test]]
         [utils :only [proj wrong-usage android-parameters ensure-paths
                       dev-build? get-project-file absolutize]]]))

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
  (let [build-steps (if library ["build"] ["code-gen" "build" "apk" "deploy"])]
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
     (when (and (nil? project) (not (#{"new" "help" "init"} cmd)))
       (abort "Subtask" cmd "should be run from the project folder."))
     (ensure-paths (-> (proj) :android :sdk-path))
     (doto (android-parameters (proj))
       extract-aar-dependencies
       (execute-subtask cmd args))))

(defn conditional-execute-subtask
  "Conditionally execute the specified subtask."
  [{:as project, :keys [root target-path]} subtask]
  (ensure-paths root)
  (let [dependencies (map #(absolutize root %)
                          ((ib/get-subtask-dependencies project) subtask))
        timestamp-file-name (str target-path "/timestamps.txt")]
    (debug "Dependecies for" subtask "are" dependencies)
    (when (ib/input-modified? timestamp-file-name subtask dependencies)
      (info "Dependecies are modified since last recorded time. Re-doing" subtask)
      (case subtask
        "generate-manifest" (generate-manifest project)
        "generate-resource-code" (generate-resource-code project)
        "generate-build-constants" (generate-build-constants project)
        "compile" (compile project)
        "create-dex" (create-dex project)
        (println "Unregonized subtask: " subtask))
      (ib/record-timestamps timestamp-file-name subtask dependencies))))

(defn conditional-code-gen
  "Initiate the conditional execution of metatask code-gen. We identify
   which subtasks in code-gen are required to be run."
  [project]
  (info "Running conditional code generation")
    (doto project
      (conditional-execute-subtask "generate-manifest")
      (conditional-execute-subtask "generate-resource-code")
      (conditional-execute-subtask "generate-build-constants")))

(defn conditional-build
  "Interactively build the components that are necessary to be built, i.e. build stale
   resources only. Do not build that's not updated since the last time."
  [project]
  (info "dependencies for crete-dex" ((ib/get-subtask-dependencies project) "crete-dex"))
  (info "Running conditional build")
  (doto project
    (conditional-execute-subtask "compile")
    (create-dex)))

(defn execute-subtask
  "Executes a subtask defined by `name` on the given project."
  [project name args]
  (case name
    ;; Standalone tasks
    "new" (if (< (count args) 2)
            (abort (wrong-usage "lein droid new" #'new))
            (apply new args))
    "init" (init (.getAbsolutePath (clojure.java.io/file ".")))
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
    "code-gen" (conditional-code-gen project)
    "build" (conditional-build project)
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
