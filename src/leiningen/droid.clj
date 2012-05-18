;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:use [clojure [repl :only (doc source)]]
        [leiningen.droid.compile :only (compile)]
        [leiningen.droid.build :only [create-dex crunch-resources
                                      package-resources create-apk
                                      sign-apk zipalign-apk install apk build]]
        [leiningen.droid.run :only [run forward-port repl]]
        [leiningen.droid.new :only [create-project]]
        [leiningen.droid.utils :only (proj)]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.core.project :only (read) :rename {read read-project}]))

(defn print-subtask-list
  "Show the list of possible lein droid subtasks."
  [droid-var]
  (println (subtask-help-for "" droid-var)))

(defn foo
  "This function just prints the project map."
  [project & args]
  (println project))

(defn doall
  "Performs all Android tasks from compilation to the deployment."
  [project]
  (doto project
    compile create-dex
    crunch-resources package-resources
    create-apk sign-apk zipalign-apk
    install run))

(defn ^{:no-project-needed true
        :subtasks [#'compile #'create-dex #'crunch-resources #'package-resources
                   #'create-apk #'sign-apk #'zipalign-apk #'install #'run
                   #'doall #'apk #'build]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([])
  ([project]
     (print-subtask-list #'droid))
  ([project & [cmd & _ :as args]]
     (case cmd
       "new" (create-project "blablaproject" "org.blabla.foo")
       "neko" (apply compile (leiningen.droid.utils/proj "/home/unlogic/clojure/android/neko/project.clj") _)
       "repl" (repl (proj))
       "forward" (forward-port (proj))
       "doall" (doall (proj))
       "run" (run (proj))
       "install" (install (proj))
       "apk" (apk (proj))
       "zipalign" (zipalign-apk (proj))
       "sign" (sign-apk (proj))
       "create-apk" (create-apk (proj))
       "package-resources" (package-resources (proj))
       "crunch-resources" (crunch-resources (proj))
       "create-dex" (create-dex (proj))
       "compile" (compile (proj))
       "build" (build (proj))
       "foo" (foo (proj))
       "help" (print-subtask-list #'droid))))
