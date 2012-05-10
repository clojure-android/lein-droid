;; Provides utilities for the plugin.
;;
(ns leiningen.droid.utils
  (:use [clojure.java.io :only (file)]
        [leiningen.core.main :only (debug info) :rename {debug print-debug}]
        [leiningen.core.project :only (read) :rename {read read-project}]
        [clojure.string :only (join)]))

;; ### Middleware section

(defn absolutize
  "Taken from Leiningen source code.

  Absolutizes the `path` given `root` if it is relative. Leaves the
  path as is if it is absolute."
  [root path]
  (str (if (.isAbsolute (file path))
         path
         (file root path))))

(defn absolutize-android-paths
  "Taken from Leiningen source code.

  Absolutizes all values with keys ending with `path` or `paths` in
  the `:android` map of the project."
  [{:keys [root android] :as project}]
  (assoc project :android
         (into {} (for [[key val] android]
                    [key (cond (re-find #"-path$" (name key))
                                      (absolutize root val)

                                      (re-find #"-paths$" (name key))
                                      (map (partial absolutize root) val)

                                      :else val)]))))

(defn get-default-android-params
  "Returns a map of the default android-specific parameters."
  [project-name target-path]
  {:out-dex-path (str target-path "/classes.dex")
   :manifest-path "AndroidManifest.xml"
   :res-path "res"
   :out-res-path (str target-path "/res")
   :assets-path "assets"
   :out-res-pkg-path (str target-path "/" project-name ".ap_")
   :out-apk-path (str target-path "/" project-name ".apk")
   :keystore-path (str (System/getenv "HOME") "/.android/debug.keystore")
   :repl-device-port 9999
   :repl-local-port 9999})

;; This is the middleware function to be plugged into project.clj.
(defn android-parameters
  "Merges project's `:android` map with the default parameters map and
  absolutizes paths in the `android` map."
  [{:keys [name target-path android] :as project}]
  (let [android-params (merge (get-default-android-params name target-path)
                              android)]
    (absolutize-android-paths
     (assoc project :android android-params))))

;; ### General utilities

(defn proj [] (read-project "sample/project.clj"))

(defn get-sdk-platform-path
  "Returns a version-specific path to the Android platform tools."
  [sdk-root version]
  (format "%s/platforms/android-%s" sdk-root version))

(defn get-sdk-android-jar
  "Returns a version-specific path to the `android.jar` SDK file."
  [sdk-root version]
  (str (get-sdk-platform-path sdk-root version) "/android.jar"))

(defn process-jar-path
  "Given a jar-file from the Maven repository parses its path and
  returns the information about it."
  [f]
  (let [[_ group name major minor patch]
        (re-find #".+/([^/]+)/([^/]+)/(\d+)\.(\d+)\.(\d+)(\-SNAPSHOT)?/.+"
                 (str f))]
    {:name name :group group :file f
     :major major :minor minor :patch patch}))

;;  Since `dx` and `apkbuilder` utilities fail when they are feeded
;;  repeated jar-files, we need this function to return only unique
;;  jars for the project.
(defn unique-jars
  "Returns the list of unique jars regardless of version or groupId.
Android-patched version of Clojure is prefered over the other ones.
For the rest the latest version is preferred. Implies that all
dependencies come from the local Maven storage.

This function should be rewritten in future."
  [jars]
  (let [grouped (group-by :name (map process-jar-path jars))]
    (for [[name same-jars] grouped]
      ;; For Clojure jar choose only from Android-specific versions.
      (let [same-jars (if (= name "clojure")
                        (filter #(= (:group %) "android") same-jars)
                        same-jars)]
        (:file (first
                (sort-by #(vec (map % [:major :minor :patch]))
                         (comp - compare) same-jars)))))))

(defn first-matched
  "Returns the first item from the collection predicate `pred` for
  which returns logical truth."
  [pred coll]
  (some (fn [item] (when (pred item) item)) coll))

(defn sh
  "Executes the command given by `args` in a subprocess."
  [& args]
  (info (join (interpose " " args)))
  (.exec (Runtime/getRuntime) (join (interpose " " args))))