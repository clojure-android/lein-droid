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
         [build :only [create-dex crunch-resources package-resources create-apk
                       sign-apk zipalign-apk apk build]]
         [device :only [get-device-args install run forward-port repl]]
         [new :only [new]]
         [utils :only [proj wrong-usage android-parameters ensure-paths]]]))

(defn help
  "Show the list of possible lein droid subtasks."
  [droid-var]
  (println (subtask-help-for "" droid-var)))

(defn foo
  "This function just prints the project map."
  [project & args]
  (println project))

(defn doall
  "Performs all Android tasks from compilation to the deployment."
  [{{:keys [adb-bin]} :android :as project} & device-args]
  (println device-args)
  (doto project build apk)
  (ensure-paths adb-bin)
  (let [device (get-device-args adb-bin device-args)]
    (apply install project device)
    (apply run project device)
    (apply forward-port project device)))

(defn release
  "Builds, packs and deploys the release version of the project."
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
        :subtasks [#'new #'compile #'create-dex #'crunch-resources
                   #'package-resources #'create-apk #'sign-apk #'zipalign-apk
                   #'install #'run #'forward-port #'repl #'build #'apk #'doall
                   #'help]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([project]
     (help #'droid))
  ([project & [cmd & args]]
     (case cmd
       ;; Standalone tasks
       "new" (if (< (count args) 2)
               (abort (wrong-usage "lein droid new" #'new))
               (apply new args))
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

       ;; Meta tasks
       "build" (build project)
       "apk" (apk project)
       "doall" (apply doall project)
       "release" (release project)

       ;; Help tasks
       "foo" (foo project)
       "help" (help #'droid))))
