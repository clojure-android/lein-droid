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
         [utils :only [proj wrong-usage android-parameters ensure-paths]]]))

(defn help
  "Shows the list of possible `lein droid` subtasks."
  ([]) ([droid-var]
          (println "lein-droid is a plugin for Clojure/Android development."
                   (subtask-help-for nil droid-var))))

(defn pprint
  "Pretty-prints a representation of the project map."
  [project & keys]
  (if (seq keys)
    (doseq [k keys]
      (clojure.pprint/pprint (get project (read-string k))))
    (clojure.pprint/pprint project))
  (flush))

(declare execute-subtask)

(defn doall
  "Metatask. Performs all Android tasks from compilation to the
  deployment using the default android-dev and android-config
  profiles"
  [{{:keys [library]} :android :as project} & device-args]
  (let [dev-project (-> project
                        (set-profiles [:android-dev :android-config])
                        android-parameters)
        build-steps (if library ["build"] ["build" "apk" "deploy"])]
    (doseq [task build-steps]
      (execute-subtask dev-project task device-args))))

(def ^{:doc "Default set of tasks to create an application release."}
  release-routine ["clean" "build" "apk" "deploy"])

(defn release
  "Metatask. Builds, packs and deploys the release version of the
  project using the default android-release and android-config
  profiles.

  Can also take optional list of subtasks to execute (instead of
  executing all of them) and arguments to `adb` for deploying."
  [project & args]
  (let [;; adb-args should be in the end of the argument list.
        [subtasks adb-args] (split-with #(not (.startsWith % "-")) args)
        subtasks (if (seq subtasks)
                   subtasks release-routine)
        release-project (-> project
                            (set-profiles [:android-release :android-config])
                            (assoc-in [:android :build-type] :release)
                            android-parameters)]
    (doseq [task subtasks]
      (execute-subtask release-project task adb-args))))

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
    "pprint" (pprint project)
    "help" (help #'droid)

    (println "Subtask is not recognized:" name
             (subtask-help-for nil #'droid))))
