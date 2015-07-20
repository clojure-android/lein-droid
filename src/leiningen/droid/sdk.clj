(ns leiningen.droid.sdk
  "Functions to interact with Android SDK tools."
  (:use [leiningen.core.main :only [debug]])
  (:require [cemerick.pomegranate :as pomegranate]
            [leiningen.droid.aar :refer [get-aar-native-paths]]
            [clojure.java.io :as io])
  (:import java.io.File java.io.PrintStream))

(defn- make-apk-builder
  "Uses reflection to make an ApkBuilder instance."
  [apk-name res-path dex-path]
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        constructor (. apkbuilder-class getConstructor
                       (into-array [File File File String PrintStream]))]
    (.newInstance constructor (into-array [(io/file apk-name) (io/file res-path)
                                           nil nil nil]))))

(defn- get-unpacked-natives-paths
  "Returns paths to unpacked native libraries if they exist, nil otherwise."
  []
  (let [path "target/native/linux/"]
    (when (.exists (io/file path))
      [path])))

(defn create-apk
  "Delegates APK creation to ApkBuilder class in sdklib.jar."
  [{{:keys [sdk-path out-res-pkg-path out-dex-path native-libraries-paths]}
    :android :as project} & {:keys [apk-name resource-jars]}]
  ;; Dynamically load sdklib.jar
  (pomegranate/add-classpath (io/file sdk-path "tools" "lib" "sdklib.jar"))
  (let [apkbuilder (make-apk-builder apk-name out-res-pkg-path out-dex-path)
        all-native-libraries (concat native-libraries-paths
                                     (get-unpacked-natives-paths)
                                     (get-aar-native-paths project))
        dexes (filter #(re-matches #".*dex" (.getName %))
                      (.listFiles (io/file out-dex-path)))]
    (when (seq resource-jars)
      (debug "Adding resource libraries: " resource-jars)
      (doseq [rj resource-jars]
        (.addResourcesFromJar apkbuilder rj)))
    (when (seq all-native-libraries)
      (debug "Adding native libraries: " all-native-libraries)
      (doseq [lib all-native-libraries]
        (.addNativeLibraries apkbuilder ^File (io/file lib))))
    (when (seq dexes)
      (debug "Adding DEX files: " dexes)
      (doseq [dex dexes]
        (.addFile apkbuilder dex (.getName dex))))
    (.sealApk apkbuilder)))
