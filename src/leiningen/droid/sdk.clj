(ns leiningen.droid.sdk
  "Functions to interact with Android SDK tools."
  (:use [leiningen.core.main :only [debug abort]])
  (:require [leiningen.droid.aar :refer [get-aar-native-paths]]
            [leiningen.droid.sideload :as sideload]
            [clojure.java.io :as io])
  (:import java.io.File))

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
  (sideload/sideload-jars sdk-path)
  (let [apkbuilder (sideload/apk-builder apk-name out-res-pkg-path out-dex-path)
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
    (if (seq dexes)
      (do
        (debug "Adding DEX files: " dexes)
        (doseq [dex dexes]
          (.addFile apkbuilder dex (.getName dex))))
      (abort "No *.dex files found in " out-dex-path))
    (.sealApk apkbuilder)))
