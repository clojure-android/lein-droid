;; This part of the plugin is responsible for compilation of the
;; project. Generally **lein-droid** tries to reuse as much Leiningen code
;; as possible, but there are a few exceptions.
;;
(ns leiningen.droid.compile
  (:refer-clojure :exclude (compile))
  (:require leiningen.compile leiningen.javac)
  (:use [leiningen.droid.utils :only (get-sdk-platform-path)]
        [robert.hooke :only (add-hook)]))

(defn compile-java
  "Compiles Java files that come with the project. The paths to these
  files are specified by `:source-paths` in project.clj. Note that the
  value of `:source-paths` should be a vector of strings."
  [project & args]
  (apply leiningen.javac/javac project args))

;; Now before defining the actual `compile` function we have to
;; manually attach Android SDK libraries to the classpath. The reason
;; for this is that Leiningen doesn't handle external dependencies at
;; the high level, that's why we hack `get-classpath` function.

(defn classpath-hook
  "Takes the original `get-classpath` function and the project map,
extracting `:android-sdk-path` and `:android-target-version` values
from it. Then the path to the actual **android.jar** file is
constructed and appended to the rest of the classpath list."
  [f {:keys [android-sdk-path android-target-version] :as project}]
  (let [result (cons (str (get-sdk-platform-path android-sdk-path
                                                 android-target-version)
                          "/android.jar")
                     (f project))]
    (println result)
    result))

(add-hook #'leiningen.core.classpath/get-classpath #'classpath-hook)

(defn compile-clojure
  "Compiles Clojure files of the project."
  [project & args]
  (apply leiningen.compile/compile project args))

(defn compile
  "Compiles both Java and Clojure source files."
  [project & args]
  (apply compile-java project args)
  (apply compile-clojure project args))
