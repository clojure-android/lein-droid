(ns leiningen.droid.sdk
  "Convenient function to interact with utilities in Android SDK."
  (:require [cemerick.pomegranate :as pomegranate]
            [clojure.java.io :as io])
  (:import java.io.File java.io.PrintStream))

(defn- make-apk-builder
  "Uses reflection to make an ApkBuilder instance."
  [apk-name res-path dex-path]
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        constructor (.getConstructor apkbuilder-class
                                     (into-array [File File File
                                                  String PrintStream]))]
    (.newInstance constructor (into-array [(io/file apk-name) (io/file res-path)
                                           (io/file dex-path) nil nil]))))

(defn create-apk
  "Delegates APK creation to ApkBuilder class in sdklib.jar."
  [{{:keys [sdk-path out-res-pkg-path out-dex-path]}
    :android} & {:keys [apk-name resource-jars]}]
  ;; Dynamically load sdklib.jar
  (pomegranate/add-classpath (io/file sdk-path "tools" "lib" "sdklib.jar"))
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        apkbuilder (make-apk-builder apk-name out-res-pkg-path out-dex-path)]
    (doseq [rj resource-jars]
      (.addResourcesFromJar apkbuilder rj))
    (.sealApk apkbuilder)))
