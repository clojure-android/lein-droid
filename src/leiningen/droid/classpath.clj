(ns leiningen.droid.classpath
  "Contains functions and hooks for Android-specific classpath
  manipulation."
  (:require [leiningen.droid.aar :refer [get-aar-files]])
  (:use [robert.hooke :only [add-hook]]
        [leiningen.droid.utils :only [get-sdk-android-jar
                                      get-sdk-google-api-jars
                                      get-sdk-support-jars]])
  (:import org.sonatype.aether.util.version.GenericVersionScheme))

;; Since `dx` and `ApkBuilder` utilities fail when they are feeded
;; repeated jar-files, we need to make sure that JAR dependencies list
;; contains only unique jars.

(defn remove-duplicate-dependencies
  "Filters project's dependency list for unique jars regardless of
  version or groupId. Android-patched version of Clojure is prefered
  over the other ones. For the rest the latest version is preferred."
  [dependencies]
  (let [tagged (for [[artifact version :as dep] dependencies]
                 (let [[_ group name] (re-matches #"(.+/)?(.+)" (str artifact))]
                   {:name name, :group group, :ver version, :original dep}))
        grouped (group-by :name tagged)
        scheme (GenericVersionScheme.)]
    (for [[name same-jars] grouped]
      ;; For Clojure jar choose only from Android-specific versions
      ;; (if there is at least one).
      (let [same-jars (if (= name "clojure")
                        (let [droid-clojures (filter #(= (:group %)
                                                         "org.clojure-android/")
                                                     same-jars)]
                          (if-not (empty? droid-clojures)
                            droid-clojures
                            same-jars))
                        same-jars)]
        (:original
         (reduce #(if (pos? (compare (.parseVersion scheme (or (:version %2)
                                                               "0"))
                                     (.parseVersion scheme (or (:version %1)
                                                               "0"))))
                    %2 %1)
                 same-jars))))))

(defn- dependencies-hook
  "Takes the original `get-dependencies` function and arguments to it.
  Removes duplicate entries from the result when resolving project
  dependencies."
  [f dependency-key project & rest]
  (let [all-deps (apply f dependency-key project rest)]
    (if (= dependency-key :dependencies)
      ;; aether/dependency-files expects a map but uses keys only,
      ;; so we transform a list into a map with nil values.
      (zipmap (remove-duplicate-dependencies (keys all-deps))
              (repeat nil))
      all-deps)))

(defn- resolve-dependencies-hook
  "Takes the original `resolve-dependencies` function and arguments to it.
  Appends jar files extracted from AAR dependencies."
  [f dependency-key project & rest]
  (let [deps (apply f dependency-key project rest)]
    (if (= dependency-key :dependencies)
      (concat deps (get-aar-files project "classes.jar"))
      deps)))

;; We also have to manually attach Android SDK libraries to the
;; classpath. The reason for this is that Leiningen doesn't handle
;; external dependencies at the high level, and Android jars are not
;; distributed in a convenient fashion (using Maven repositories). To
;; solve this we hack into `get-classpath` function.

(defn classpath-hook
  "Takes the original `get-classpath` function and the project map,
  extracting the path to the Android SDK and the target version from it.
  Then the path to the actual `android.jar` file is constructed and
  appended to the rest of the classpath list."
  [f {{:keys [sdk-path target-version external-classes-paths
              use-google-api support-libraries]}
      :android :as project}]
  (let [classpath (f project)
        result (conj (concat classpath external-classes-paths
                             (when use-google-api
                               (get-sdk-google-api-jars sdk-path
                                                        target-version))
                             (get-sdk-support-jars sdk-path support-libraries))
                     (get-sdk-android-jar sdk-path target-version)
                     (str sdk-path "/tools/support/annotations.jar"))]
    result))

(defn init-hooks []
  (add-hook #'leiningen.core.classpath/get-dependencies #'dependencies-hook)
  (add-hook #'leiningen.core.classpath/resolve-dependencies #'resolve-dependencies-hook)
  (add-hook #'leiningen.core.classpath/get-classpath #'classpath-hook))
