(ns leiningen.droid.compile
  "This part of the plugin is responsible for the project compilation."
  (:refer-clojure :exclude [compile])
  (:require leiningen.compile leiningen.javac
            [clojure.java.io :as io]
            [leiningen.core.eval :as eval])
  (:use [leiningen.droid.utils :only [get-sdk-android-jar
                                      ensure-paths sh dev-build?]]
        [leiningen.core
         [main :only [debug info abort]]
         [classpath :only [get-classpath]]]
        [bultitude.core :only [namespaces-on-classpath]]))

(defn code-gen
  "Generates the R.java file from the resources.

  This task is necessary if you define the UI in XML and also to gain
  access to your strings and images by their ID."
  [{{:keys [sdk-path target-version manifest-path res-path gen-path
            out-res-path external-res-paths library]} :android}]
  (info "Generating R.java...")
  (let [aapt-bin (str sdk-path "/platform-tools/aapt")
        android-jar (get-sdk-android-jar sdk-path target-version)
        manifest-file (io/file manifest-path)
        library-specific (if library "--non-constant-id" "--auto-add-overlay")
        external-resources (for [res external-res-paths] ["-S" res])]
    (ensure-paths sdk-path manifest-path res-path aapt-bin android-jar)
    (.mkdirs (io/file gen-path))
    (.mkdirs (io/file out-res-path))
    (sh aapt-bin "package" library-specific "-f" "-m"
        "-M" manifest-path
        "-S" out-res-path
        "-S" res-path
        external-resources
        "-I" android-jar
        "-J" gen-path
        "--generate-dependencies")))

(defn compile-clojure
  "Taken partly from Leiningen source code.

  Compiles Clojure files into .class files.

  If `:aot` project parameter equals `:all` then compiles the
  necessary dependencies. If `:aot` equals `:all-with-unused` then
  compiles all namespaces of the dependencies whether they were
  referenced in the code or not. The latter is useful for the
  REPL-driven development.

  Uses neko to set compilation flags. Some neko macros and
  subsequently project code depends on them to eliminate
  debug-specific code when building the release."
  [{{:keys [enable-dynamic-compilation start-nrepl-server]} :android,
    :keys [aot aot-exclude-ns] :as project}]
  (info "Compiling Clojure files...")
  (debug "Project classpath:" (get-classpath project))
  (let [nses
        (case aot
          :all
            (conj (seq (leiningen.compile/stale-namespaces project)) 'neko.init)
          :all-with-unused
            (namespaces-on-classpath :classpath
                                     (map io/file (get-classpath project)))
          (conj (map symbol aot) 'neko.init))
        nses (remove (set (map symbol aot-exclude-ns)) nses)
        dev-build (dev-build? project)]
    (info (format "Build type: %s, dynamic compilation: %s, remote REPL: %s."
                  (if dev-build "debug" "release")
                  (if (or dev-build start-nrepl-server
                          enable-dynamic-compilation)
                    "enabled" "disabled")
                  (if (or dev-build start-nrepl-server) "enabled" "disabled")))
    (let [form `(neko.init/with-properties
                  [:android-dynamic-compilation ~enable-dynamic-compilation
                   :android-start-nrepl-server ~start-nrepl-server
                   :android-release-build ~(not dev-build)]
                  (doseq [namespace# '~nses]
                    (println "Compiling" namespace#)
                    (clojure.core/compile namespace#)))
          project (update-in project [:prep-tasks]
                             (partial remove #{"compile"}))]
      (.mkdirs (io/file (:compile-path project)))
      (try (eval/eval-in-project project form '(require 'neko.init))
           (info "Compilation succeeded.")
           (catch Exception e
             (abort "Compilation failed."))))))

(defn compile
  "Compiles both Java and Clojure source files."
  [{{:keys [sdk-path]} :android, java-only :java-only :as project} & args]
  (ensure-paths sdk-path)
  (apply leiningen.javac/javac project args)
  (when-not java-only
   (compile-clojure project)))
