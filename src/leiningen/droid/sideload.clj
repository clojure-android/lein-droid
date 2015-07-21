(ns leiningen.droid.sideload
  "Wrappers around classes and methods that we pull dynamically from jars in
  Android SDK."
  (:require [cemerick.pomegranate :as cp]
            [clojure.java.io :as io])
  (:import [java.io File PrintStream]))

(def sideload-jars
  "Dynamically adds jars from Android SDK on the classpath."
  (memoize (fn [sdk-path]
             (cp/add-classpath (io/file sdk-path "tools" "lib" "sdklib.jar")))))

(defn apk-builder
  "Uses reflection to make an ApkBuilder instance."
  [apk-name res-path dex-path]
  (let [apkbuilder-class (Class/forName "com.android.sdklib.build.ApkBuilder")
        constructor (. apkbuilder-class getConstructor
                       (into-array [File File File String PrintStream]))]
    (.newInstance constructor (into-array [(io/file apk-name) (io/file res-path)
                                           nil nil nil]))))

(defn symbol-loader
  "Uses reflection to make an SymbolLoader instance."
  [file]
  (let [sl-class (Class/forName "com.android.sdklib.internal.build.SymbolLoader")
        constructor (. sl-class getConstructor (into-array [File]))]
    (.newInstance constructor (into-array [(io/file file)]))))

(defn symbol-writer
  "Uses reflection to make an SymbolLoader instance."
  [out-folder package-name full-symbols]
  (let [sl-class (Class/forName "com.android.sdklib.internal.build.SymbolLoader")
        sw-class (Class/forName "com.android.sdklib.internal.build.SymbolWriter")
        constructor (. sw-class getConstructor (into-array [String String sl-class]))]
    (.newInstance constructor (into-array Object [out-folder package-name full-symbols]))))
