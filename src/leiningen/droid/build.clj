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
                       sdk-binary relativize-path]]
         [manifest :only [write-manifest-with-internet-permission]]])
  (:require [clojure.string]
            [clojure.set]
            [clojure.java.io :as io]
            [leiningen.droid.sdk :as sdk]
            leiningen.jar leiningen.javac)
  (:import java.io.File))

;; ### Build-related subtasks

;; Since the execution of `dx` takes a pretty lot of time we need to
;; ensure that its subprocess will be killed if user cancels the build
;; (sends SIGINT to leiningen). That is why we add a hook to the
;; runtime that will be triggered when Leiningen is closed.
;;
(defn- run-dx
  "Run dex on the given target which should be either directory with .class
files or jar file, e.g. one produced by proguard."
  [{{:keys [sdk-path out-dex-path external-classes-paths
            force-dex-optimize dex-opts]} :android,
            :as project}
   target]
  (ensure-paths sdk-path)
  (let [dx-bin (sdk-binary sdk-path :dx)
        options (or dex-opts [])
        no-optimize (if (and (not force-dex-optimize) (dev-build? project))
                      "--no-optimize" [])
        annotations (str sdk-path "/tools/support/annotations.jar")
        deps (resolve-dependencies :dependencies project)
        external-classes-paths (or external-classes-paths [])]
    (with-process [proc (map str
                             (flatten [dx-bin options "--dex" no-optimize
                                       "--output" out-dex-path
                                       target annotations deps
                                       external-classes-paths]))]
      (.addShutdownHook (Runtime/getRuntime) (Thread. #(.destroy proc))))))


(defn create-dex
  "Creates a DEX file from the compiled .class files."
  [{compile-path :compile-path :as project}]
  (info "Creating DEX....")
  (ensure-paths compile-path)
  (run-dx project compile-path))

(defn create-obfuscated-dex
  "Creates an obfuscated DEX file from the compiled .class files."
  [{{:keys [sdk-path out-dex-path external-classes-paths
            force-dex-optimize dex-opts dex-aux-opts
            proguard-conf-path target-version proguard-opts]} :android,
            compile-path :compile-path
            project-name :name
            target-path :target-path
            :as project}]
  (info "Creating obfuscated DEX....")
  (ensure-paths sdk-path compile-path proguard-conf-path)
  (when-not (.isDirectory (io/file compile-path))
    (abort (format "compile-path (%s) is not a directory" compile-path)))

  (let [obfuscated-jar-file (str (io/file target-path (str project-name "-obfuscated.jar")))
        proguard-jar (sdk-binary sdk-path :proguard)
        android-jar (get-sdk-android-jar sdk-path target-version)
        proguard-opts (or proguard-opts [])

        annotations (str sdk-path "/tools/support/annotations.jar")
        deps (resolve-dependencies :dependencies project)
        external-paths (or external-classes-paths [])

        compile-path-dir (io/file compile-path)

        ;; to figure out what classes were thrown away by proguard
        orig-class-files
        (when *debug*
          (into #{} (for [file (file-seq compile-path-dir)
                          :when (and (.isFile ^File file)
                                     (.endsWith (str file) ".class"))]
                      (relativize-path compile-path-dir file))))]
    (sh "java"
        "-jar" proguard-jar
        (str "@" proguard-conf-path)
        "-injars" compile-path
        "-outjars" obfuscated-jar-file
        "-libraryjars" (->> (concat [annotations android-jar]
                                    deps external-paths)
                            (map str)
                            (clojure.string/join ":"))
        proguard-opts)
    (when *debug*
      (let [optimized-class-files
            (for [file (binding [*debug* false]
                         ;; Supress this output
                         (sh "jar" "tf" obfuscated-jar-file))
                  :let [trimmed (clojure.string/trim-newline file)]
                  :when (.endsWith ^String trimmed ".class")]
              trimmed)

            thrown-away-classes (clojure.set/difference orig-class-files
                                                        optimized-class-files)]
        (cond (empty? thrown-away-classes) nil

              (< (count thrown-away-classes) 30)
              (doseq [class thrown-away-classes]
                (debug class))

              :else
              (let [file (io/file target-path "removed-classes.txt")]
                (debug
                 (format "%s classes were removed by ProGuard. See list in %s."
                         (count thrown-away-classes) file))
                (spit file (clojure.string/join "\n" thrown-away-classes))))))
    (run-dx project obfuscated-jar-file)))

(defn crunch-resources
  "Updates the pre-processed PNG cache.

  Calls `aapt` binary with the _crunch_ task."
  [{{:keys [sdk-path res-path out-res-path]} :android}]
  (info "Crunching resources...")
  (ensure-paths sdk-path res-path)
  (let [aapt-bin (sdk-binary sdk-path :aapt)]
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
  [{{:keys [sdk-path target-version manifest-path assets-path res-path
            out-res-path external-res-paths out-res-pkg-path]} :android
            :as project}]
  (info "Packaging resources...")
  (ensure-paths sdk-path manifest-path res-path)
  (let [aapt-bin (sdk-binary sdk-path :aapt)
        android-jar (get-sdk-android-jar sdk-path target-version)
        dev-build (dev-build? project)
        manifest-file (io/file manifest-path)
        backup-file (io/file (str manifest-path ".backup"))
        ;; Only add `assets` directory if it is present.
        assets (if (.exists (io/file assets-path)) ["-A" assets-path] [])
        external-resources (for [res external-res-paths] ["-S" res])]
    (when dev-build
      (io/copy manifest-file backup-file)
      (write-manifest-with-internet-permission manifest-path))
    (sh aapt-bin "package" "--no-crunch" "-f" "--debug-mode" "--auto-add-overlay"
        "-M" manifest-path
        "-S" out-res-path
        "-S" res-path
        external-resources
        assets
        "-I" android-jar
        "-F" out-res-pkg-path
        "--generate-dependencies")
    (when dev-build
      (io/copy backup-file manifest-file)
      (io/delete-file backup-file))))

(defn create-apk
  "Creates a deployment-ready APK file.

  It is done by executing methods from ApkBuilder SDK class on the
  generated DEX-file and the resource package."
  [{{:keys [sdk-path out-apk-path out-res-pkg-path
            out-dex-path resource-jars-paths]} :android,
            java-only :java-only :as project}]
  (info "Creating APK...")
  (ensure-paths sdk-path out-res-pkg-path out-dex-path)
  (let [suffix (if (dev-build? project) "debug-unaligned" "unaligned")
        unaligned-path (append-suffix out-apk-path suffix)
        [clojure-jar] (filter #(re-find #"org.clojure-android[/\\]clojure"
                                        (str %))
                              (resolve-dependencies :dependencies project))
        resource-jars (concat (when-not java-only [clojure-jar])
                              (map #(java.io.File. %) resource-jars-paths))]
    (sdk/create-apk project
                    :apk-name unaligned-path :resource-jars resource-jars)))

(defn sign-apk
  "Signs APK file with the key taken from the keystore.

  Either a debug keystore key or a release key is used based on
  whether the build type is the debug one. Creates a debug keystore if
  it is missing."
  [{{:keys [out-apk-path
            keystore-path key-alias keypass storepass]} :android :as project}]
  (info "Signing APK...")
  (let [dev-build (dev-build? project)
        suffix (if dev-build "debug-unaligned" "unaligned")
        unaligned-path (append-suffix out-apk-path suffix)
        storepass (cond storepass storepass
                        dev-build "android"
                        :else
                        (read-password "Enter storepass: "))
        keypass (cond keypass keypass
                      dev-build "android"
                      :else
                      (read-password "Enter keypass: "))]
    (when (and dev-build (not (.exists (io/file keystore-path))))
      ;; Create a debug keystore if there isn't one
      (create-debug-keystore keystore-path))
    (ensure-paths unaligned-path keystore-path)
    (sh "jarsigner"
        "-sigalg" "MD5withRSA"
        "-digestalg" "SHA1"
        "-keystore" keystore-path
        "-storepass" storepass
        "-keypass" keypass
        unaligned-path key-alias)))

(defn zipalign-apk
  "Aligns resources locations on 4-byte boundaries in the APK file.

  Done by calling `zipalign` binary on APK file."
  [{{:keys [sdk-path out-apk-path]} :android :as project}]
  (info "Aligning APK...")
  (let [zipalign-bin (sdk-binary sdk-path :zipalign)
        unaligned-suffix (if (dev-build? project) "debug-unaligned" "unaligned")
        unaligned-path (append-suffix out-apk-path unaligned-suffix)
        aligned-path (if (dev-build? project)
                       (append-suffix out-apk-path "debug")
                       out-apk-path)]
    (ensure-paths sdk-path unaligned-path)
    (.delete (io/file aligned-path))
    (sh zipalign-bin "4" unaligned-path aligned-path)))

(defn apk
  "Metatask. Crunches and packages resources, creates, signs and aligns an APK."
  [project]
  (doto project
    crunch-resources package-resources
    create-apk sign-apk zipalign-apk))
