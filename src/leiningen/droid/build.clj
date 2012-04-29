;; This namespace covers all the points regarding the build process of
;; an Android project.
;;
(ns leiningen.droid.build
  (:use [leiningen.core.classpath :only (get-classpath resolve-dependencies)]
        [leiningen.droid.utils :only [get-sdk-platform-path]]
        [clojure.string :only (join)]
        [clojure.java.io :only (file)]))

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
  [{:keys [android-sdk-path compile-path] :as project}]
  (let [dx-bin (str android-sdk-path "/platform-tools/dx")
        out-dex (str compile-path "/classes.dex")
        annotations (str android-sdk-path "/tools/support/annotations.jar")
        deps (resolve-dependencies :dependencies project)
        process (apply sh dx-bin "--dex" "--output" annotations deps)]
    (. (Runtime/getRuntime) addShutdownHook (Thread. #(.destroy process)))
    (. process waitFor)))

(defn crunch-resources
  "Calls `aapt` binary with the _crunch_ task. Takes optional
  `:android-res-path` and `:android-out-res-path` from `project`
  values or uses the default ones."
  [{:keys [root android-sdk-path android-res-path android-out-res-path
           target-path]}]
  (println "Crunching resources..." android-res-path android-out-res-path)
  (let [appt-bin (str android-sdk-path "/platform-tools/aapt")
        android-res-path (or android-res-path (str root "/res"))
        android-out-res-path (or android-out-res-path
                                 (str target-path "/res"))]
    (.mkdirs (file android-out-res-path))
    (.waitFor (sh appt-bin "crunch -v -S" android-res-path
                  "-C" android-out-res-path))))

(defn package-resources
  "Calls `aapt` binary with the _package_ task."
  [{:keys [root android-manifest android-sdk-path android-target-version
           android-assets-path android-res-path android-out-res-path
           android-resource-package target-path name]}]
  (println "Packaging resources...")
  (let [aapt-bin (str android-sdk-path "/platform-tools/aapt")
        android-manifest (or android-manifest (str root "/AndroidManifest.xml"))
        android-res-path (or android-res-path (str root "/res"))
        android-out-res-path (or android-out-res-path
                                 (str target-path "/res"))
        android-assets-path (or android-assets-path (str root "/assets"))
        android-resource-package (or android-resource-package
                                     (str target-path "/" name ".ap_"))
        android-jar (str (get-sdk-platform-path android-sdk-path
                                                android-target-version)
                         "/android.jar")]
    (.waitFor (sh aapt-bin "package" "--no-crunch" "-f" "--debug-mode"
                  "--auto-add-overlay" "--generate-dependencies"
                  "-M" android-manifest
                  "-S" android-res-path
                  "-S" android-out-res-path
                  "-A" android-assets-path
                  "-I" android-jar
                  "-F" android-resource-package))))

#_(create-dex (leiningen.droid/proj))
#_(crunch-resources (leiningen.droid/proj))