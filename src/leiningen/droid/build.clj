(ns leiningen.droid.build
  "A set of functions and subtasks responsible for building the
  Android project."
  (:use [leiningen.core.classpath :only (resolve-dependencies)]
        [leiningen.droid.utils :only [get-sdk-android-jar unique-jars
                                      first-matched proj]]
        [leiningen.droid.manifest :only (get-launcher-activity)]
        [clojure.string :only (join)]))

;; ### Helper functions

(defn sh
  "Executes the command given by `args` in a subprocess."
  [& args]
  (println (join (interpose " " args)))
  (.exec (Runtime/getRuntime) (join (interpose " " args))))

(defn- append-suffix
  "Appends a suffix to a filename, e.g. transforming `sample.apk` into
  `sample-signed.apk`"
  [filename suffix]
  (let [[_ without-ext ext] (re-find #"(.+)(\.\w+)" filename)]
    (str without-ext "-" suffix ext)))

;; ### Subtasks

;; Since the execution of `dx` takes a pretty lot of time we need to
;; ensure that its subprocess will be killed if user cancels the build
;; (sends SIGINT to leiningen). That is why we add a hook to the
;; runtime that will be triggered when Leiningen is closed.
;;
(defn create-dex
  "Creates the DEX file from the compiled .class files. It is done by
  executing `dx` binary from Android SDK."
  [{{:keys [sdk-path out-dex-path]} :android,
    compile-path :compile-path :as project}]
  (let [dx-bin (str sdk-path "/platform-tools/dx")
        annotations (str sdk-path "/tools/support/annotations.jar")
        deps (unique-jars (resolve-dependencies :dependencies project))
        process (apply sh dx-bin "--dex" "--output" out-dex-path
                       compile-path annotations deps)]
    (. (Runtime/getRuntime) addShutdownHook (Thread. #(.destroy process)))
    (. process waitFor)))

(defn crunch-resources
  "Calls `aapt` binary with the _crunch_ task."
  [{{:keys [sdk-path res-path out-res-path]} :android}]
  (println "Crunching resources...")
  (let [aapt-bin (str sdk-path "/platform-tools/aapt")]
    (.waitFor (sh aapt-bin "crunch -v -S" res-path
                  "-C" out-res-path))))

(defn package-resources
  "Calls `aapt` binary with the _package_ task."
  [{{:keys [sdk-path target-version manifest-path assets-path res-path
            out-res-path out-res-pkg-path]} :android}]
  (println "Packaging resources...")
  (let [aapt-bin (str sdk-path "/platform-tools/aapt")
        android-jar (get-sdk-android-jar sdk-path target-version)]
    (.waitFor (sh aapt-bin "package" "--no-crunch" "-f" "--debug-mode"
                  "-M" manifest-path "-S" out-res-path "-S" res-path
                  "-A" assets-path "-I" android-jar "-F" out-res-pkg-path
                  "--generate-dependencies"))))

;; Note that from all dependencies we add only the Clojure one.
;; Without it the application won't start.
;;
(defn create-apk
  "Creates an APK file by running `apkbuilder` tool on the generated
  DEX-file and resource package. The output APK file has a
  _-debug-unaligned_ suffix."
  [{{:keys [sdk-path out-apk-path out-res-pkg-path out-dex-path]} :android,
    source-paths :source-paths, java-source-paths :java-source-paths
    :as project}]
  (println "Creating APK...")
  (let [apkbuilder-bin (str sdk-path "/tools/apkbuilder")
        unaligned-path (append-suffix out-apk-path "debug-unaligned")
        source-paths-args (mapcat #(vector "-rf" %) (concat source-paths
                                                            java-source-paths))
        clojure-jar (first-matched #(re-find #"android/clojure" (str %))
                          (resolve-dependencies :dependencies project))]
    (.waitFor (apply sh apkbuilder-bin unaligned-path "-u"
                     "-z" out-res-pkg-path
                     "-f" out-dex-path
                     "-rj" clojure-jar
                     source-paths-args))))

(defn sign-apk
  "Signs APK file with a key from the debug keystore."
  [{{:keys [out-apk-path keystore-path]} :android}]
  (println "Signing APK...")
  (let [unaligned-path (append-suffix out-apk-path "debug-unaligned")]
    (.waitFor (sh "jarsigner"
                 "-keystore" keystore-path
                 "-storepass" "android"
                 "-keypass" "android"
                 unaligned-path "androiddebugkey"))))

(defn zipalign-apk
  "Calls `zipalign` binary on APK file. The output APK file has
  _-debug_ suffix."
  [{{:keys [sdk-path out-apk-path]} :android}]
  (println "Aligning APK...")
  (let [zipalign-bin (str sdk-path "/tools/zipalign")
        unaligned-path (append-suffix out-apk-path "debug-unaligned")
        aligned-path (append-suffix out-apk-path "debug")]
    (.waitFor (sh zipalign-bin "4" unaligned-path aligned-path))))

(defn install
  "Installs the current debug APK to the connected device."
  [{{:keys [sdk-path out-apk-path]} :android}]
  (println "Installing APK...")
  (let [adb-bin (str sdk-path "/platform-tools/adb")
        apk-debug-path (append-suffix out-apk-path "debug")]
    (.waitFor (sh adb-bin "-d" "install"
                  "-r" apk-debug-path))))

(defn run
  "Launches the installed APK on the connected device."
  [{{:keys [sdk-path manifest-path]} :android}]
  (println "Launching APK...")
  (let [adb-bin (str sdk-path "/platform-tools/adb")]
    (.waitFor (sh adb-bin "shell am start"
                  "-n" (get-launcher-activity manifest-path)))))

(comment
  (create-dex (leiningen.droid.utils/proj))
  (crunch-resources (leiningen.droid.utils/proj))
  (package-resources (leiningen.droid.utils/proj))
  (create-apk (leiningen.droid.utils/proj))
  (sign-apk (leiningen.droid.utils/proj))
  (zipalign-apk (leiningen.droid.utils/proj))
  (install (leiningen.droid.utils/proj))
  (run (leiningen.droid.utils/proj))
  )