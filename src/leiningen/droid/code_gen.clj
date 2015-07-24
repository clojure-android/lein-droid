(ns leiningen.droid.code-gen
  "Tasks and related functions for build-specific code generation."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clostache.parser :as clostache]
            [leiningen.core.main :refer [debug info abort]]
            [leiningen.droid.aar :refer [get-aar-files]]
            [leiningen.droid.manifest :refer [get-package-name generate-manifest]]
            [leiningen.droid.sideload :as sideload]
            [leiningen.droid.utils :refer [get-sdk-android-jar sdk-binary
                                           ensure-paths sh dev-build?]]
            [leiningen.new.templates :refer [slurp-resource]])
  (:import java.io.File))

;; ### BuildConfig.java generation

(defn- java-type
  "Mapping of classes to type strings as they should appear in BuildConfig."
  [x]
  (condp = (type x)
    Boolean "boolean"
    String  "String"
    Long    "long"
    Double  "double"
    (abort ":build-config only supports boolean, String, long and double types.")))

(defn map-constants
  "Transform a map of constants return to form readable by Clostache."
  [constants]
  (map (fn [[k v]]
         (binding [*print-dup* true]
           {:key k
            :value (pr-str v)
            :type (java-type v)}))
       constants))

(defn generate-build-constants
  [{{:keys [manifest-path gen-path build-config rename-manifest-package]}
    :android, version :version :as project}]
  (ensure-paths manifest-path)
  (let [res (io/resource "templates/BuildConfig.java")
        package-name (get-package-name manifest-path)
        gen-package-path (apply io/file gen-path (str/split package-name #"\."))
        application-id (or rename-manifest-package package-name)
        template-constants (-> (merge {"VERSION_NAME"   version
                                       "APPLICATION_ID" application-id}
                                      build-config)
                               map-constants)]
    (ensure-paths gen-package-path)
    (->> {:debug        (dev-build? project)
          :package-name package-name
          :constants    template-constants}
         (clostache/render (slurp-resource res))
         (spit (io/file gen-package-path "BuildConfig.java")))))

;; ### R.java generation

(defn create-r-file
  "Generates R.java file given full symbols file, library symbols file and
  library package name. Symbols file are loaded from respective R.txt files."
  [full-symbols lib-r-txt lib-package gen-path]
  (debug "Generating R.java file for:" lib-package)
  (let [symbols (sideload/symbol-loader lib-r-txt)
        writer (sideload/symbol-writer (str gen-path) lib-package full-symbols)]
    (.load symbols)
    (.addSymbolsToWrite writer symbols)
    (.write writer)))

(defn generate-r-files
  "Generates R.java files for the project and all dependency libraries, having
  R.txt for project and each library."
  [{{:keys [sdk-path gen-path manifest-path]} :android :as project}]
  (sideload/sideload-jars sdk-path)
  (let [full-r-txt (io/file gen-path "R.txt")
        full-symbols (sideload/symbol-loader full-r-txt)]
    (.load full-symbols)
    (dorun
     (map (fn [manifest, ^File r-txt]
            (when (.exists r-txt)
              (let [package-name (get-package-name manifest)
                    lib-gen-path gen-path]
                (create-r-file full-symbols r-txt package-name lib-gen-path))))
          (get-aar-files project "AndroidManifest.xml")
          (get-aar-files project "R.txt")))))

(defn generate-resource-code
  "Generates R.java files for both the project and the libraries."
  [{{:keys [sdk-path target-version manifest-path res-path gen-path
            out-res-path external-res-paths library]} :android
            java-only :java-only :as project}]
  (info "Generating R.java files...")
  (let [aapt-bin (sdk-binary project :aapt)
        android-jar (get-sdk-android-jar sdk-path target-version)
        manifest-file (io/file manifest-path)
        library-specific (if library ["--non-constant-id"] [])
        aar-resources (for [res (get-aar-files project "res")] ["-S" (str res)])
        external-resources (for [res external-res-paths] ["-S" res])]
    (ensure-paths manifest-path res-path android-jar)
    (.mkdirs (io/file gen-path))
    (.mkdirs (io/file out-res-path))
    (sh aapt-bin "package" library-specific "-f" "-m"
        "-M" manifest-path
        "-S" out-res-path
        "-S" res-path
        aar-resources
        external-resources
        "-I" android-jar
        "-J" gen-path
        "--output-text-symbols" gen-path
        "--auto-add-overlay"
        "--generate-dependencies")
    ;; Finally generate R.java files having R.txt keys
    (when-not library
      (generate-r-files project))))

(defn code-gen
  "Generates R.java and builds a manifest with the appropriate version
  code and substitutions."
  [{{:keys [library]} :android :as project}]
  (doto project
    generate-manifest generate-resource-code
    generate-build-constants))
