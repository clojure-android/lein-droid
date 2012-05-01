;; This namespace covers all the points regarding the build process of
;; an Android project.
;;
(ns leiningen.droid.build
  (:use [leiningen.core.classpath :only (resolve-dependencies)]
        [leiningen.droid.utils :only [get-sdk-android-jar]]
        [clojure.string :only (join)]
        [clojure.java.io :only (file copy)]))

(defn sh
  "Executes the command given by `args` in a subprocess."
  [& args]
  (println (join (interpose " " args)))
  (.exec (Runtime/getRuntime) (join (interpose " " args))))

;; Since the execution of `dx` takes a pretty lot of time we need
;; to ensure that its subprocess will be killed if user cancels the
;; build (sends SIGINT to leiningen). That is why we add a hook to the
;; runtime that will be triggered when the leiningen is closed.
;;
(defn create-dex
  "Creates the DEX file from the compiled .class files. It is done by
  executing `dx` binary from Android SDK."
  [{:keys [android-sdk-path android-out-dex-path] :as project}]
  (let [dx-bin (str android-sdk-path "/platform-tools/dx")
        annotations (str android-sdk-path "/tools/support/annotations.jar")
        deps (resolve-dependencies :dependencies project)
        process (apply sh dx-bin "--dex" "--output" android-out-dex-path
                       annotations deps)]
    (. (Runtime/getRuntime) addShutdownHook (Thread. #(.destroy process)))
    (. process waitFor)))

(defn crunch-resources
  "Calls `aapt` binary with the _crunch_ task. Takes optional
  `:android-res-path` and `:android-out-res-path` from `project`
  values or uses the default ones."
  [{:keys [root android-sdk-path android-res-path android-out-res-path]}]
  (println "Crunching resources..." android-res-path android-out-res-path)
  (let [appt-bin (str android-sdk-path "/platform-tools/aapt")]
    (.mkdirs (file android-out-res-path))
    (.waitFor (sh appt-bin "crunch -v -S" android-res-path
                  "-C" android-out-res-path))))

(defn package-resources
  "Calls `aapt` binary with the _package_ task."
  [{:keys [android-manifest-path android-sdk-path android-target-version
           android-assets-path android-res-path android-out-res-path
           android-out-res-pkg-path]}]
  (println "Packaging resources...")
  (let [aapt-bin (str android-sdk-path "/platform-tools/aapt")
        android-jar (get-sdk-android-jar android-sdk-path
                                         android-target-version)]
    (.waitFor (sh aapt-bin "package" "--no-crunch" "-f" "--debug-mode"
                  "--auto-add-overlay" "--generate-dependencies"
                  "-M" android-manifest-path
                  "-S" android-res-path
                  "-S" android-out-res-path
                  "-A" android-assets-path
                  "-I" android-jar
                  "-F" android-out-res-pkg-path))))

(defn- append-suffix
  "Helper function that appends a suffix to a filename, e.g.
  transforming `sample.apk` into `sample-signed.apk`"
  [apk-name suffix]
  (let [[_ without-ext ext] (re-find #"(.+)(\.\w+)" apk-name)]
    (str without-ext "-" suffix ext)))

(defn create-apk
  "Creates an APK file by running `apkbuilder` tool on generated
  DEX-file and resource package. The output APK file has a
  _-debug-unaligned_ suffix."
  [{:keys [android-sdk-path
  android-out-apk-path android-out-res-pkg-path
  android-out-dex-path]}]
  (println "Creating APK...")
  (let [apkbuilder-bin (str android-sdk-path "/tools/apkbuilder")
        unaligned-path (append-suffix android-out-apk-path "debug-unaligned")]
    (.waitFor (sh apkbuilder-bin unaligned-path "-u"
                  "-z" android-out-res-pkg-path
                  "-f" android-out-dex-path))))

(defn sign-apk-debug
  "Signs APK file with a key from the debug.keystore."
  [{:keys [android-out-apk-path android-debug-keystore-path]}]
  (println "Signing APK (debug)...")
  (let [unaligned-path (append-suffix android-out-apk-path "debug-unaligned")]
    (.waitFor (sh "jarsigner"
                 "-keystore" android-debug-keystore-path
                 "-storepass" "android"
                 "-keypass" "android"
                 unaligned-path "androiddebugkey"))))

(defn zipalign-apk
  "Calls `zipalign` binary on APK file. The output APK file has
  _-debug_ suffix."
  [{:keys [android-sdk-path android-out-apk-path]}]
  (println "Aligning APK...")
  (let [zipalign-bin (str android-sdk-path "/tools/zipalign")
        unaligned-path (append-suffix android-out-apk-path "debug-unaligned")
        aligned-path (append-suffix android-out-apk-path "debug")]
    (.waitFor (sh zipalign-bin "4" unaligned-path aligned-path))))

#_(create-dex (leiningen.droid/proj))
#_(crunch-resources (leiningen.droid/proj))
#_(package-resources (leiningen.droid/proj))
#_(create-apk (leiningen.droid/proj))
#_(sign-apk-debug (leiningen.droid/proj))
#_(zipalign-apk (leiningen.droid/proj))
