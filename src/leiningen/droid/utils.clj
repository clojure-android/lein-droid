;; Provides utilities for the plugin.
;;
(ns leiningen.droid.utils
  (:use [clojure.java.io :only (file reader)]
        [leiningen.core.main :only (info debug abort)]
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
  [{{sdk-path :sdk-path} :android, name :name, target-path :target-path}]
  {:out-dex-path (str target-path "/classes.dex")
   :manifest-path "AndroidManifest.xml"
   :res-path "res"
   :gen-path "gen"
   :out-res-path (str target-path "/res")
   :assets-path "assets"
   :out-res-pkg-path (str target-path "/" name ".ap_")
   :out-apk-path (str target-path "/" name ".apk")
   :keystore-path (str (System/getenv "HOME") "/.android/debug.keystore")
   :adb-bin (str sdk-path "/platform-tools/adb")
   :key-alias "androiddebugkey"
   :repl-device-port 9999
   :repl-local-port 9999
   :target-version 10})

;; This is the middleware function to be plugged into project.clj.
(defn android-parameters
  "Merges project's `:android` map with the default parameters map and
  absolutizes paths in the `android` map."
  [{:keys [android] :as project}]
  (let [android-params (merge (get-default-android-params project)
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
           output# (line-seq (reader (.getInputStream ~process-name)))]
       ~@body
       (.waitFor ~process-name)
       (if-not (= (.exitValue ~process-name) 0)
         (apply abort output#)
         (apply debug output#))
       output#)))

(defn sh
  "Executes the command given by `args` in a subprocess. Flattens the
  given list."
  [& args]
  (with-process [process (flatten args)]))

(defn dev-build?
  "Checks if the current Leiningen run contains :dev profile."
  [project]
  (contains? (-> project meta :included-profiles set) :dev))

(defmacro ensure-paths
  "Checks if the given directories or files exist. Aborts Leiningen
  execution in case either of the doesn't or the value equals nil."
  [& paths]
  `(do
     ~@(for [p paths]
         `(cond (nil? ~p)
                (abort "The value of" (str '~p) "is nil. Abort execution.")

                (not (.exists (file ~p)))
                (abort "The path" ~p "doesn't exist. Abort execution.")))))

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

(defn read-password
  "Reads the password from the console without echoing the
  characters."
  [prompt]
  (join (.readPassword (System/console) prompt nil)))

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
      "-keyalg" "RSA"
      "-keysize" "1024"
      "-validity" "365"
      "-keypass" "android"
      "-storepass" "android"
      "-dname" "CN=Android Debug,O=Android,C=US"))

(defmacro with-properties
  "Sets the given properties to the respective values to run the code
  provided in `body`. At the end restore the initial property values."
  [bindings & body]
  (let [bindings (partition 2 bindings)
        initial (gensym)]
    `(let [~initial (hash-map ~@(mapcat (fn [[prop _]]
                                          [prop `(System/getProperty ~prop)])
                                        bindings))]
       ~@(for [[prop newval] bindings]
           `(System/setProperty ~prop (str ~newval)))
       (let [result# (do ~@body)]
         ~@(for [[prop newval] bindings]
             `(System/setProperty ~prop (get ~initial ~prop)))
         result#))))
