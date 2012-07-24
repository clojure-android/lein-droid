;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:use [leiningen.clean :only [delete-file-recursively]]
        [leiningen.core.project :only [merge-profiles unmerge-profiles]]
        [leiningen.core.main :only [abort]]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.droid.compile :only (compile code-gen)]
        [leiningen.droid
         [classpath :only [init-hooks]]
         [build :only [create-dex crunch-resources package-resources create-apk
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

(defn foo
  "This function just prints the project map."
  [project & args]
  (println project))

(defn doall
  "Metatask. Performs all Android tasks from compilation to the deployment."
  [{{:keys [library]} :android :as project} & device-args]
  (if library
    (build project)
    (do (doto project
          build apk)
        (apply deploy project device-args))))

(defn release
  "Metatask. Builds, packs and deploys the release version of the project."
  [project]
  (let [release-project (-> project
                            (unmerge-profiles [:dev])
                            (merge-profiles [:release])
                            android-parameters)]
    (delete-file-recursively (:compile-path project) :silently)
    (build release-project)
    (apk release-project)
    (install release-project)))

(defn ^{:no-project-needed true
        :subtasks [#'new #'init #'code-gen #'compile #'create-dex
                   #'crunch-resources #'package-resources #'create-apk
                   #'sign-apk #'zipalign-apk #'install #'run #'forward-port
                   #'repl #'build #'apk #'deploy #'doall #'release #'help
                   #'gather-dependencies]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([project]
     (help #'droid))
  ([project & [cmd & args]]
     (when (and (nil? project) (not (#{"new" "help" "init"} cmd)))
       (abort "This subtask requires to be run from the project folder."))
     (init-hooks)
     (let [;; Poor man's middleware here
           project (when project (android-parameters project))]
       (case cmd
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
         "gather-dependencies" (apply gather-dependencies project args)

         ;; Meta tasks
         "build" (build project)
         "apk" (apk project)
         "deploy" (apply deploy project args)
         "doall" (apply doall project args)
         "release" (release (android-parameters project))
         "jar" (jar project)

         ;; Help tasks
         "foo" (foo project)
         "help" (help #'droid)))))
