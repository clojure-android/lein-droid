;; Generally **lein-droid** tries to reuse as much Leiningen code as
;; possible. Compilation subtasks in this namespace just call their
;; Leiningen counterparts.
;;
(ns leiningen.droid.compile
  "This part of the plugin is responsible for the project compilation."
  (:refer-clojure :exclude (compile))
  (:require leiningen.compile leiningen.javac)
  (:use [leiningen.droid.utils :only (get-sdk-android-jar unique-jars)]
        [robert.hooke :only (add-hook)]))

(defn compile-java
  "Compiles Java files that come with the project. The paths to these
  files are specified by `:java-source-paths` in project.clj. Note
  that the value of `:java-source-paths` should be a vector of strings."
  [project & args]
  (apply leiningen.javac/javac project args))

;; Now before defining the actual `compile` function we have to
;; manually attach Android SDK libraries to the classpath. The reason
;; for this is that Leiningen doesn't handle external dependencies at
;; the high level, that's why we hack `get-classpath` function.

(defn classpath-hook
  "Takes the original `get-classpath` function and the project map,
extracting the path to the Android SDK and the target version from it.
Then the path to the actual `android.jar` file is constructed and
appended to the rest of the classpath list. Also removes all duplicate
jars from the classpath."
  [f {{:keys [sdk-path target-version]} :android :as project}]
  (let [classpath (f project)
        [jars paths] ((juxt filter remove) #(re-matches #".+\.jar" %) classpath)
        result (conj (concat (unique-jars jars) paths)
                     (get-sdk-android-jar sdk-path target-version)
                     (str sdk-path "/tools/support/annotations.jar"))]
    (println result)
    result))

(add-hook #'leiningen.core.classpath/get-classpath #'classpath-hook)

(defn compile-clojure
  "Compiles Clojure files of the project. Also compiles the dependencies."
  [project & args]
  (apply leiningen.compile/compile project args))

(defn compile
  "Compiles both Java and Clojure source files."
  [project & args]
  (apply compile-java project args)
  (apply compile-clojure project args))
