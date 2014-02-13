(ns leiningen.droid.sdk
  "Convenient function to interact with utilities in Android SDK."
  (:use [leiningen.core.main :only [debug]])
  (:require [cemerick.pomegranate :as pomegranate]
            [clojure.java.io :as io]
            [leiningen.core.classpath :refer [get-classpath]])
  (:import java.io.File java.io.PrintStream java.util.jar.JarFile))

(defn- make-apk-builder
  "Uses reflection to make an ApkBuilder instance."
  [apk-name res-path dex-path]
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        constructor (.getConstructor apkbuilder-class
                                     (into-array [File File File
                                                  String PrintStream]))]
    (.newInstance constructor (into-array [(io/file apk-name) (io/file res-path)
                                           (io/file dex-path) nil nil]))))

(defn- entry-matching-native-android-lib
  "Given a JarFile and a JarEntry, returns metadata of the .so library
  it represents, or nil if it doesn't represent a native library"
  [jarfile entry & [allow-debug?]]
  (when-let [[fullpath dir arch so-name] (re-find #"(.*native/linux/)(armeabi(?:-v7a)?|x86)(?:\\|\/)([^\\|\/]*.so)" (.getName entry))]
    (when (or allow-debug? (not (.endsWith so-name "-g.so")))
      {:fullpath fullpath
       :dir dir
       :arch arch
       :so-name so-name
       :jar-file jarfile
       :zip-entry entry})))

(defn- jar-android-native-libs
  "Given a jarname, returns metadata for each of the entries in the
  jar file that match"
  [jarname]
  (let [jarfile (JarFile. jarname)]
    (->> jarfile
         (.entries)
         (enumeration-seq)
         (keep (partial entry-matching-native-android-lib jarfile)))))

(defn- jars-containing-native-libraries
  "Looks through the classpath for any jars that match the pattern of
  native/linux/<arch> where arch is an android supported architecture
  armeabi, armeabi-v7a or x86"
  [project]
  (->> (get-classpath project)
       (filter #(.endsWith % "jar"))
       (mapcat jar-android-native-libs)))

(defn- copy-native-libraries-from-jar
  "Given .so file metadata as returned by
  entry-matching-native-android-lib and a native-libraries path,
  extract the libraries to the libraries path"
  [native-libraries-paths {:keys [so-name arch jar-file zip-entry]}]
  (debug "Extracting native lib from jar for inclusion in apk: " (.getName jar-file) zip-entry)
  (let [output (io/file native-libraries-paths arch so-name)
        input (.getInputStream jar-file zip-entry)]
    (io/make-parents output)
    (io/copy input output)))

(defn create-apk
  "Delegates APK creation to ApkBuilder class in sdklib.jar."
  [{{:keys [sdk-path out-res-pkg-path out-dex-path native-libraries-paths extract-native-libraries-from-jars?]} :android :as project}
   & {:keys [apk-name resource-jars]}]
  ;; Dynamically load sdklib.jar
  (pomegranate/add-classpath (io/file sdk-path "tools" "lib" "sdklib.jar"))
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        apkbuilder (make-apk-builder apk-name out-res-pkg-path out-dex-path)]
    (when (seq resource-jars)
      (debug "Adding resource libraries: " resource-jars))
    (doseq [rj resource-jars]
      (.addResourcesFromJar apkbuilder rj))
    
    (when extract-native-libraries-from-jars?
      (debug "Searching jars for native libraries to include")
      (let [extracted-libs-path (io/file "target/extracted-libs/")]
        (doseq [entry (jars-containing-native-libraries project)]
          (copy-native-libraries-from-jar extracted-libs-path entry))
        (.addNativeLibraries apkbuilder ^File extracted-libs-path)))
    
    (when (seq native-libraries-paths)
      (debug "Adding native libraries: " native-libraries-paths))
    (doseq [lib native-libraries-paths]
      (.addNativeLibraries apkbuilder ^File (io/file lib)))
    (.sealApk apkbuilder)))
