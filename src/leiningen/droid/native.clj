(ns leiningen.droid.native
  (:require [leiningen.core.classpath :refer [get-classpath]]
            [leiningen.core.main :refer [debug]]
            [clojure.java.io :as io]
            [clojure.edn :refer [read-string]])
  (:import [java.util.jar JarFile]))

(def match-native-name
  #"(.*native/linux/)(armeabi(?:-v7a)?|x86)(?:\\|\/)([^\\|\/]*.so)")

(def extracted-libs-path (io/file "target/extracted-libs/"))

(defn entry-matching-native-android-lib
  "Given a JarFile and a JarEntry, returns metadata of the .so library it
  represents, or nil if it doesn't represent a native library"
  [jarfile entry & [allow-debug?]]
  (when-let [[fullpath dir arch so-name] (re-find match-native-name
                                                  (.getName entry))]
    (when (or allow-debug? (not (.endsWith so-name "-g.so")))
      {:fullpath fullpath
       :dir dir
       :arch arch
       :so-name so-name
       :jar-file jarfile
       :zip-entry entry})))

(defn jar-android-native-libs
  "Given a jarname, returns metadata for each of the entries in the jar file
  that match"
  [jarname]
  (let [jarfile (JarFile. jarname)]
    (->> jarfile
         (.entries)
         (enumeration-seq)
         (keep (partial entry-matching-native-android-lib jarfile)))))

(defn load-checked-jars-cache []
  (try
    (-> (io/file extracted-libs-path "cached.edn")
        slurp read-string set)
    (catch Exception e #{})))

(defn save-checked-jars-cache [coll]
  (let [cache-file (io/file extracted-libs-path "cached.edn")]
    (io/make-parents cache-file)
    (spit cache-file (binding [*print-length* nil] (pr-str coll))))
  coll)

;; Future improvement: Cache jars/versions and don't re-check if possible
(defn jars-containing-native-libraries
  "Looks through the classpath for any jars that match the pattern of
  native/linux/<arch> where arch is an android supported architecture armeabi,
  armeabi-v7a or x86"
  [project]  
  (->> (get-classpath project)
       (filter #(.endsWith % "jar"))
       (remove (load-checked-jars-cache))
       (save-checked-jars-cache)
       (mapcat jar-android-native-libs)))

;; Future improvement: avoid the copy if it's already taken place
(defn copy-native-libraries-from-jar
  "Given .so file metadata as returned by entry-matching-native-android-lib and
  a native-libraries path, extract the libraries to the libraries path"
  [native-libraries-paths {:keys [so-name arch jar-file zip-entry]}]
  (debug "Extracting native lib from jar for inclusion in apk: "
         (.getName jar-file) zip-entry)
  (let [output (io/file native-libraries-paths arch so-name)
        input (.getInputStream jar-file zip-entry)]
    (io/make-parents output)
    (io/copy input output)))

(defn extract-so-from-uberjars
  "Takes a project and checks the classpath for uberjars, if it finds any
  extracts them and adds them to the native library path for the apk"
  [project]  
  (let [active (get-in project [:android :extract-native-libraries-from-jars?])] 
    (when active
      (debug "Searching jars for native libraries to include")
      (doseq [entry (jars-containing-native-libraries project)]
        (copy-native-libraries-from-jar extracted-libs-path entry)))
    
    (if active
      (update-in project [:android :native-libraries-paths]
                 conj extracted-libs-path)
      project)))
