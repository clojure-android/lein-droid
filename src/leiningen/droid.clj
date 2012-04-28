;; ## Clojure development is simple. Android should also be.
;;
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile])
  (:use [clojure [repl :only (doc source)]]
        [leiningen.droid.compile :only (compile)]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.core.project :only (read) :rename {read read-project}]))

#_(defn proj [] (read-project "sample/project.clj"))

(defn print-subtask-list
  "Show the list of possible lein droid subtasks."
  [droid-var]
  (println (subtask-help-for "" droid-var)))

(defn foo
  "This function just prints the project map."
  [project & args]
  (println project))

(defn ^{:no-project-needed true
        :subtasks [#'foo #'compile]
        :help-arglist '([foo compile])}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([])
  ([project]
     (print-subtask-list #'droid))
  ([project & [cmd & _ :as args]]
     (case cmd
       "cpl" (apply compile project args)
       "foo" (apply foo project args)
       "help" (print-subtask-list #'droid))))