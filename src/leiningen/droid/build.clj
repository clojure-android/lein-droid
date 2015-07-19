(ns leiningen.droid.build
  "A set of functions and subtasks responsible for building the
  Android project."
  (:refer-clojure :exclude [compile])
  (:use [leiningen.core
         [classpath :only [resolve-dependencies]]
         [main :only [debug info abort *debug*]]]
        [leiningen.droid
         [compile :only [code-gen compile]]
         [utils :only [get-sdk-android-jar sh dev-build?
                       ensure-paths with-process read-password append-suffix
                       create-debug-keystore get-project-file read-project
                       sdk-binary relativize-path get-sdk-support-jars
                       get-resource-jars]]])
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [leiningen.droid.aar :refer [extract-aar-dependencies]]
            [leiningen.droid.sdk :as sdk]
            leiningen.jar leiningen.javac))

;; ### Build-related subtasks

;; Since the execution of `dx` takes a pretty lot of time we need to
;; ensure that its subprocess will be killed if user cancels the build
;; (sends SIGINT to leiningen). That is why we add a hook to the
;; runtime that will be triggered when Leiningen is closed.
;;
(defn- run-dx
  "Run dex on the given target paths, each should be either a directory with
  .class files or a jar file."
  [{{:keys [sdk-path out-dex-path force-dex-optimize dex-opts]} :android,
    :as project}
   target-paths]
  (info "Creating DEX....")
  (let [dx-bin (sdk-binary project :dx)
        options (or dex-opts [])
        no-optimize (if (and (not force-dex-optimize) (dev-build? project))
                      "--no-optimize" [])
        annotations (io/file sdk-path "tools" "support" "annotations.jar")]
    (with-process [proc (->> [dx-bin options "--dex" no-optimize
                              "--output" out-dex-path
                              target-paths annotations]
                             flatten
                             (map str))]
      (.addShutdownHook (Runtime/getRuntime) (Thread. #(.destroy proc))))))

(defn- run-proguard
  "Run proguard on the compiled classes and dependencies, create an JAR with
  minimized and shaken classes."
  [{{:keys [sdk-path external-classes-paths target-version support-libraries
            proguard-conf-path proguard-opts proguard-output-jar-path]} :android
            compile-path :compile-path
            :as project}]
  (info "Running Proguard...")
  (ensure-paths compile-path proguard-conf-path)

  (let [proguard-bin (sdk-binary project :proguard)
        android-jar (get-sdk-android-jar sdk-path target-version)
        proguard-opts (or proguard-opts [])

        annotations (io/file sdk-path "tools" "support" "annotations.jar")
        deps (resolve-dependencies :dependencies project)
        support-jars (get-sdk-support-jars sdk-path support-libraries true)
        external-paths (or external-classes-paths [])
        compile-path-dir (io/file compile-path)]
    (sh proguard-bin (str "@" proguard-conf-path)
        "-injars" (->> (concat [compile-path] deps external-paths support-jars)
                       (map str)
                       (str/join ":"))
        "-libraryjars" (->> [annotations android-jar]
                            (map str)
                            (str/join ":"))
        "-outjars" proguard-output-jar-path
        proguard-opts)))

(defn create-dex
  "Creates a DEX file from the compiled .class files."
  [{{:keys [sdk-path external-classes-paths support-libraries
            proguard-execute proguard-output-jar-path]} :android,
            compile-path :compile-path :as project}]
  (if proguard-execute
    (do
      (run-proguard project)
      (run-dx project proguard-output-jar-path))
    (let [deps (resolve-dependencies :dependencies project)
          support-jars (get-sdk-support-jars sdk-path support-libraries true)
          external-classes-paths (or external-classes-paths [])]
      (run-dx project (concat [compile-path] deps support-jars
                              external-classes-paths)))))

(defn crunch-resources
  "Updates the pre-processed PNG cache.

  Calls `aapt` binary with the _crunch_ task."
  [{{:keys [res-path out-res-path]} :android :as project}]
  (info "Crunching resources...")
  (ensure-paths res-path)
  (let [aapt-bin (sdk-binary project :aapt)]
    (sh aapt-bin "crunch -v"
        "-S" res-path
        "-C" out-res-path)))

;; We have to declare a future reference here because `build` and
;; `build-project-dependencies` are mutually-recursive.
;;
(declare build)

(defn build-project-dependencies
  "Builds all project dependencies for the current project."
  [{{:keys [project-dependencies]} :android, root :root}]
  (doseq [dep-path project-dependencies
          :let [dep-project (read-project (get-project-file root dep-path))]]
    (info "Building project dependency" dep-path "...")
    (build dep-project)
    (info "Building dependency complete.")))

(defn build
  "Metatask. Builds dependencies, compiles and creates DEX (if not a library)."
  [{{:keys [library]} :android :as project}]
  (if library
    (doto project
      build-project-dependencies code-gen compile crunch-resources)
    (doto project
      build-project-dependencies code-gen compile create-dex)))

(defn jar
  "Metatask. Packages compiled Java files and Clojure sources into JAR.

  Same as `lein jar` but appends Android libraries to the classpath
  while compiling Java files."
  [project]
  (extract-aar-dependencies project)
  (leiningen.javac/javac project)
  (leiningen.jar/jar project))

;; ### APK-related subtasks

(defn package-resources
  "Packages application resources.

  If this task is run with :dev profile, then it ensures that
  AndroidManifest.xml has Internet permission for running the REPL
  server. This is achieved by backing up the original manifest file
  and creating a new one with Internet permission appended to it.
  After the packaging the original manifest file is restored."
  [{{:keys [sdk-path target-version manifest-path assets-paths res-path
            out-res-path external-res-paths out-res-pkg-path
            rename-manifest-package assets-gen-path]} :android :as project}]
  (info "Packaging resources...")
  (ensure-paths sdk-path manifest-path res-path)
  (let [aapt-bin (sdk-binary project :aapt)
        android-jar (get-sdk-android-jar sdk-path target-version)
        debug-mode (if (dev-build? project) ["--debug-mode"] [])
        manifest-file (io/file manifest-path)
        backup-file (io/file (str manifest-path ".backup"))
        ;; Only add `assets` directory if it is present.
        assets (mapcat #(when (.exists (io/file %)) ["-A" %])
                       (conj assets-paths assets-gen-path))
        external-resources (for [res external-res-paths] ["-S" res])]
    (sh aapt-bin "package" "--no-crunch" "-f" debug-mode "--auto-add-overlay"
        "-M" manifest-path
        "-S" out-res-path
        "-S" res-path
        external-resources
        assets
        "-I" android-jar
        "-F" out-res-pkg-path
        "--generate-dependencies"
        (if rename-manifest-package
          ["--rename-manifest-package" rename-manifest-package] []))))

(defn create-apk
  "Creates a deployment-ready APK file.

  It is done by executing methods from ApkBuilder SDK class on the
  generated DEX-file and the resource package."
  [{{:keys [out-apk-path out-res-pkg-path
            out-dex-path resource-jars-paths]} :android,
            java-only :java-only :as project}]
  (info "Creating APK...")
  (ensure-paths out-res-pkg-path out-dex-path)
  (let [unaligned-path (append-suffix out-apk-path "unaligned")
        resource-jars (concat (get-resource-jars project)
                              (map io/file resource-jars-paths))]
    (sdk/create-apk project
                    :apk-name unaligned-path :resource-jars resource-jars)))

(defn sign-apk
  "Signs APK file with the key taken from the keystore.

  Either a debug keystore key or a release key is used based on
  whether the build type is the debug one. Creates a debug keystore if
  it is missing."
  [{{:keys [out-apk-path sigalg use-debug-keystore
            keystore-path key-alias keypass storepass]} :android :as project}]
  (info "Signing APK with" keystore-path "...")
  (let [debug (or (dev-build? project) use-debug-keystore)
        unaligned-path (append-suffix out-apk-path "unaligned")
        sigalg (or sigalg "SHA1withRSA")]
    (when (and debug (not (.exists (io/file keystore-path))))
      ;; Create a debug keystore if there isn't one
      (create-debug-keystore keystore-path))
    (ensure-paths unaligned-path keystore-path)
    (let [storepass (or (when debug "android")
                        storepass
                        (System/getenv "STOREPASS")
                        (read-password "Enter storepass: "))
          keypass   (or (when debug "android")
                        keypass
                        (System/getenv "KEYPASS")
                        (read-password "Enter keypass: "))]
      (sh "jarsigner"
          "-sigalg" sigalg
          "-digestalg" "SHA1"
          "-keystore" keystore-path
          "-storepass" storepass
          "-keypass" keypass
          unaligned-path key-alias))))

(defn zipalign-apk
  "Aligns resources locations on 4-byte boundaries in the APK file.

  Done by calling `zipalign` binary on APK file."
  [{{:keys [sdk-path out-apk-path]} :android :as project}]
  (info "Aligning APK...")
  (let [zipalign-bin (sdk-binary project :zipalign)
        unaligned-path (append-suffix out-apk-path "unaligned")]
    (ensure-paths unaligned-path)
    (.delete (io/file out-apk-path))
    (sh zipalign-bin "4" unaligned-path out-apk-path)))

(defn apk
  "Metatask. Crunches and packages resources, creates, signs and aligns an APK."
  [project]
  (doto project
    crunch-resources package-resources
    create-apk sign-apk zipalign-apk))
