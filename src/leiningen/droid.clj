;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:require clojure.pprint)
  (:use [leiningen.core.project :only [set-profiles]]
        [leiningen.core.main :only [abort]]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.clean :only [clean]]
        [leiningen.droid.compile :only [compile code-gen]]
        [leiningen.droid
         [classpath :only [init-hooks]]
         [build :only [create-dex create-obfuscated-dex
                       crunch-resources package-resources create-apk
                       sign-apk zipalign-apk apk build jar]]
         [deploy :only [install run forward-port repl deploy]]
         [new :only [new init]]
         [compatibility :only [gather-dependencies]]
         [utils :only [proj wrong-usage android-parameters ensure-paths
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
  (let [build-steps (if library ["build"] ["build" "apk" "deploy"])
        build-steps (if (dev-build? project)
                      build-steps (cons "clean" build-steps))]
    (doseq [task build-steps]
      (execute-subtask project task device-args))))

(defn release
  "DEPRECATED. Metatask. Builds, packs and deploys the release version of the
  project."
  [project & args]
  (abort "Release subtask is deprecated, please use 'lein with-profile release droid doall'"))

(defn ^{:no-project-needed true
        :subtasks [#'new #'init #'code-gen #'compile
                   #'create-dex #'create-obfuscated-dex
                   #'crunch-resources #'package-resources
                   #'create-apk #'sign-apk #'zipalign-apk
                   #'install #'run #'forward-port #'repl
                   #'build #'apk #'deploy #'doall #'release #'help
                   #'gather-dependencies #'jar #'pprint]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([project]
     (help #'droid))
  ([project & [cmd & args]]
     (init-hooks)
     (some-> project
             android-parameters
             (execute-subtask cmd args))))

(defn execute-subtask
  "Executes a subtask defined by `name` on the given project."
  [project name args]
  (when (and (nil? project) (not (#{"new" "help" "init"} name)))
    (abort "Subtask" name "should be run from the project folder."))
  (case name
    ;; Standalone tasks
    "new" (if (< (count args) 2)
            (abort (wrong-usage "lein droid new" #'new))
            (apply new args))
    "init" (init (.getAbsolutePath (clojure.java.io/file ".")))
    "code-gen" (code-gen project)
    "compile" (compile project)
    "create-dex" (create-dex project)
    "create-obfuscated-dex" (create-obfuscated-dex project)
    "crunch-resources" (crunch-resources project)
    "package-resources" (package-resources project)
    "create-apk" (create-apk project)
    "sign-apk" (sign-apk project)
    "zipalign-apk" (zipalign-apk project)
    "install" (apply install project args)
    "run" (apply run project args)
    "forward-port" (apply forward-port project args)
    "repl" (repl project)
    "gather-dependencies" (apply gather-dependencies project args)
    "clean" (clean project)

    ;; Meta tasks
    "build" (build project)
    "apk" (apk project)
    "deploy" (apply deploy project args)
    "doall" (apply doall project args)
    "release" (apply release project args)
    "jar" (jar project)

    ;; Help tasks
    "pprint" (apply pprint project args)
    "help" (help #'droid)

    (println "Subtask is not recognized:" name
             (subtask-help-for nil #'droid))))
