;; Provides utilities for the plugin.
;;
(ns leiningen.droid.utils
  (:require [leiningen.core.project :as pr]
            [robert.hooke :refer [with-hooks-disabled]])
  (:use [clojure.java.io :only (file reader)]
        [leiningen.core.main :only (info debug abort *debug*)]
        [leiningen.core.classpath :only [resolve-dependencies]]
        [clojure.string :only (join)])
  (:import [java.io File StringWriter]))

;; #### Convenient functions to run SDK binaries

(defmacro ensure-paths
  "Checks if the given directories or files exist. Aborts Leiningen
  execution in case either of them doesn't or the value equals nil.

  We assume paths to be strings or lists/vectors. The latter case is
  used exclusively for Windows batch-files which are represented like
  `cmd.exe /C batch-file`, so we test third element of the list for
  the existence."
  [& paths]
  `(do
     ~@(for [p paths]
         `(cond (nil? ~p)
                (abort "The value of" (str '~p) "is nil. Abort execution.")

                (or
                 (and (sequential? ~p) (not (.exists (file (nth ~p 2)))))
                 (and (string? ~p) (not (.exists (file ~p)))))
                (abort "The path" ~p "doesn't exist. Abort execution.")))))

(defn windows?
  "Returns true if we are running on Microsoft Windows"
  []
  (= java.io.File/separator "\\"))

(defn get-sdk-build-tools-path
  "Returns a path to the correct Android Build Tools directory."
  ([{{:keys [sdk-path build-tools-version]} :android}]
   (get-sdk-build-tools-path sdk-path build-tools-version))
  ([sdk-path build-tools-version]
   (let [bt-root-dir (file sdk-path "build-tools")
         ;; build-tools directory contains a subdir which name we don't
         ;; know that has all the tools. Let's grab the last directory
         ;; inside build-tools/ and hope it is the one we need.
         bt-dir (or build-tools-version
                    (->> (.list bt-root-dir)
                         (filter #(.isDirectory (file bt-root-dir %)))
                         sort last)
                    (abort "Build tools not found."
                           "Download them using the Android SDK Manager."))]
     (file bt-root-dir bt-dir))))

(defn sdk-binary-paths
  "Returns a map of relative paths to different SDK binaries for both
  Unix and Windows platforms."
  [sdk-path build-tools-version]
  (ensure-paths sdk-path)
  (let [build-tools (get-sdk-build-tools-path sdk-path build-tools-version)]
    {:dx {:unix (file build-tools "dx")
          :win (file build-tools "dx.bat")}
     :adb {:unix (file sdk-path "platform-tools" "adb")
           :win (file sdk-path "platform-tools" "adb.exe")}
     :aapt {:unix (file build-tools "aapt")
            :win (file build-tools "aapt.exe")}
     :zipalign {:unix (file build-tools "zipalign")
                :win (file build-tools "zipalign.exe")}
     :proguard {:unix (file sdk-path "tools" "proguard" "bin" "proguard.sh")
                :win (file sdk-path "tools" "proguard" "bin" "proguard.bat")}}))

(defn sdk-binary
  "Given the project map and the binary keyword, returns either a full
  path to the binary as a string, or a vector with call to cmd.exe for
  batch-files."
  [{{:keys [sdk-path build-tools-version]} :android} binary-kw]
  (let [binary-str (-> (sdk-binary-paths sdk-path build-tools-version)
                       (get-in [binary-kw (if (windows?) :win :unix)])
                       str)]
    (ensure-paths binary-str)
    (if (.endsWith binary-str ".bat")
      ["cmd.exe" "/C" binary-str]
      binary-str)))

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
  [{root :root, name :name, target-path :target-path
    java-paths :java-source-paths}]
  {:out-dex-path target-path
   :proguard-execute false
   :proguard-conf-path "proguard.conf"
   :proguard-output-jar-path (file target-path "mininified-classes.jar")
   :multi-dex-root-classes-path (file target-path "root-classes.jar")
   :multi-dex-main-dex-list-path (file target-path "main-dex-list.txt")
   :manifest-path (file target-path "AndroidManifest.xml")
   :manifest-main-app-path (file target-path "AndroidManifest.app.xml")
   :manifest-template-path "AndroidManifest.template.xml"
   :manifest-options {:app-name "@string/app_name"}
   :res-path "res"
   :gen-path (file target-path "gen")
   :out-res-path (file target-path "res")
   :assets-paths ["assets"]
   :assets-gen-path (file target-path "assets-gen")
   :out-res-pkg-path (file target-path (str name ".ap_"))
   :out-apk-path (file target-path (str name ".apk"))
   :keystore-path (file (System/getProperty "user.home")
                        ".android" "debug.keystore")
   :key-alias "androiddebugkey"
   :repl-device-port 9999
   :repl-local-port 9999
   :target-version 15})

(declare android-parameters)

(defn read-project
  "Reads and initializes a Leiningen project and applies Android
  middleware to it."
  [project-file]
  (android-parameters (pr/init-project (pr/read (str project-file)))))

(defn get-project-file
  "Returns the path to project.clj file in the specified project
  directory (either absolute or relative)."
  [root project-directory-path]
  (let [project-directory (file project-directory-path)]
    (if (.isAbsolute project-directory)
      (file project-directory-path "project.clj")
      (file root project-directory-path "project.clj"))))

(defn android-parameters
  "Merges project's `:android` map with default Android parameters and
  absolutizes paths in the `:android` map."
  [{:keys [android] :as project}]
  (let [android-params (merge (get-default-android-params project) android)]
    (-> project
        (vary-meta assoc-in [:profiles ::extras]
                   {:java-source-paths [(:gen-path android-params)]})
        (pr/merge-profiles [::extras])
        (assoc :android android-params)
        absolutize-android-paths)))

;; ### General utilities

(defn proj [] (read-project "sample/project.clj"))

(defn sdk-version-number
  "If version keyword is passed (for example, `:ics` or `:jelly-bean`), resolves
  it to the version number. Otherwise just returns the input."
  [kw-or-number]
  (if (keyword? kw-or-number)
    (case kw-or-number
      :ics         15
      :jelly-bean  18
      :kitkat      19
      :lollipop    21
      (abort "Unknown Android SDK version: " kw-or-number))
    kw-or-number))

(defn get-sdk-platform-path
  "Returns a version-specific path to the Android platform tools."
  [sdk-root version]
  (str (file sdk-root "platforms" (str "android-"
                                       (sdk-version-number version)))))

(defn get-sdk-android-jar
  "Returns a version-specific path to the `android.jar` SDK file."
  ([{{:keys [sdk-path target-version]} :android :as project}]
   (get-sdk-android-jar sdk-path target-version))
  ([sdk-root version]
   (str (file (get-sdk-platform-path sdk-root version) "android.jar"))))

(defn get-resource-jars
  "Get the list of dependency libraries that has `:use-resources true`
  in their definition."
  [{:keys [dependencies] :as project}]
  (let [res-deps (filter (fn [[_ _ & {:as options}]]
                           (:use-resources options))
                         (:dependencies project))
        mod-proj (assoc project :dependencies res-deps)]
    (with-hooks-disabled resolve-dependencies
      (resolve-dependencies :dependencies mod-proj))))

(def ^:dynamic *sh-print-output*
  "If true, print the output of the shell command regardless of *debug*."
  false)

(defmacro with-process
  "Executes the subprocess specified in the binding list and applies
  `body` do it while it is running. The binding list consists of a var
  name for the process and the list of strings that represents shell
  command.

  After body is executed waits for a subprocess to finish, then checks
  the exit code. If code is not zero then prints the subprocess'
  output. If in DEBUG mode print both the command and it's output even
  for the successful run."
  [[process-name command] & body]
  `(do
     (apply debug ~command)
     (let [builder# (ProcessBuilder. ~command)
           _# (.redirectErrorStream builder# true)
           ~process-name (.start builder#)
           output# (line-seq (reader (.getInputStream ~process-name)))
           out-stream# (StringWriter.)
           print-output?# (or *debug* *sh-print-output*)]
       ~@body
       (doseq [line# output#]
         (if print-output?#
           (info line#)
           (binding [*out* out-stream#]
             (println line#))))
       (.waitFor ~process-name)
       (when-not (and (= (.exitValue ~process-name) 0)
                      (not print-output?#))
         (info (str out-stream#)))
       (when-not (= (.exitValue ~process-name) 0)
         (abort "Abort execution."))
       output#)))

(defn sh
  "Executes the command given by `args` in a subprocess. Flattens the
  given list. Turns files into canonical paths."
  [& args]
  (let [str-args (for [arg (flatten args)]
                   (if (instance? File arg)
                     (.getCanonicalPath ^File arg)
                     (str arg)))]
    (with-process [process str-args])))

(defn dev-build?
  "Checks the build type of the current project, assuming dev build if
  not a release build"
  [project]
  (not= (get-in project [:android :build-type]) :release))

(defn wrong-usage
  "Returns a string with the information about the proper function usage."
  ([task-name function-var]
     (wrong-usage task-name function-var 0))
  ([task-name function-var arglist-number]
     (let [arglist (-> function-var
                       meta :arglists (nth arglist-number))
           argcount (count arglist)
           parametrify #(str "<" % ">")
           ;; Replace the destructuring construction after & with
           ;; [optional-args].
           arglist (if (= (nth arglist (- argcount 2)) '&)
                     (concat (map parametrify
                                  (take (- argcount 2) arglist))
                             ["[optional-args]"])
                     (map parametrify arglist))]
       (format "Wrong number of argumets. USAGE: %s %s"
               task-name (join (interpose " " arglist))))))

(defn prompt-user
  "Reads a string from the console until the newline character."
  [prompt]
  (print prompt)
  (flush)
  (read-line))

(defn read-password
  "Reads the password from the console without echoing the
  characters."
  [prompt]
  (if-let [console (System/console)]
    (join (.readPassword console prompt nil))
    (prompt-user prompt)))

(defn append-suffix
  "Appends a suffix to a filename, e.g. transforming `sample.apk` into
  `sample-signed.apk`"
  [filename suffix]
  (let [[_ without-ext ext] (re-find #"(.+)(\.\w+)" filename)]
    (str without-ext "-" suffix ext)))

(defn create-debug-keystore
  "Creates a keystore for signing debug APK files."
  [keystore-path]
  (sh "keytool" "-genkey" "-v"
      "-keystore" keystore-path
      "-alias" "androiddebugkey"
      "-sigalg" "SHA1withRSA"
      "-keyalg" "RSA"
      "-keysize" "1024"
      "-validity" "365"
      "-keypass" "android"
      "-storepass" "android"
      "-dname" "CN=Android Debug,O=Android,C=US"))

(defn relativize-path [^File dir ^File to-relativize]
  (.getPath (.relativize (.toURI dir)
                         (.toURI to-relativize))))
