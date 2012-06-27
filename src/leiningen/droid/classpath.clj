(ns leiningen.droid.classpath
  "Contains functions and hooks to manipulate the classpath."
  (:use [robert.hooke :only [add-hook]])
  (:import org.sonatype.aether.util.version.GenericVersionScheme))

;; Since `dx` and `apkbuilder` utilities fail when they are feeded
;; repeated jar-files, we need to make sure that JAR dependencies list
;; contains only unique jars.

(defn remove-duplicate-dependencies
  "Filters project's dependency list for unique jars regardless of
  version or groupId. Android-patched version of Clojure is prefered
  over the other ones. For the rest the latest version is preferred."
  [dependencies]
  (let [tagged (map
                (fn [[artifact version :as dep]]
                  (let [[_ group name] (re-find #"(.+/)?(.+)" (str artifact))]
                    {:name name, :group group, :ver version, :original dep}))
                dependencies)
        grouped (group-by :name tagged)
        scheme (GenericVersionScheme.)]
    (for [[name same-jars] grouped]
      ;; For Clojure jar choose only from Android-specific versions
      ;; (if there is at least one).
      (let [same-jars (if (= name "clojure")
                        (let [droid-clojures (filter #(= (:group %) "android/")
                                                     same-jars)]
                          (if-not (empty? droid-clojures)
                            droid-clojures
                            same-jars))
                        same-jars)]
        (:original
         (reduce #(if (pos? (compare (.parseVersion scheme (:version %2))
                                     (.parseVersion scheme (:version %1))))
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

(defn init-hooks []
  (add-hook #'leiningen.core.classpath/get-dependencies #'dependencies-hook))

