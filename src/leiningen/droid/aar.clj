(ns leiningen.droid.aar
  "Utilities for manipulating Android package format (AAR)."
  (:require [clojure.java.io :as io]
            [leiningen.core.classpath :as cp]
            [leiningen.core.main :refer [debug]])
  (:import java.io.File
           net.lingala.zip4j.core.ZipFile))

(defn- get-aar-dependencies
  "Returns a list of artifact dependencies that have `aar` extension."
  [project]
  (let [deps (cp/get-dependencies :dependencies project)]
    (for [[[art-id ver & opts :as dep]] deps
          :let [opts (apply hash-map opts)]
          :when (= (:extension opts) "aar")]
      dep)))

(defn- str-dependency
  "Takes a dependency vector and returns its stringified version to be used in a
  file system."
  [dep]
  (-> (meta dep)
      :dependency
      .getArtifact
      str
      (.replace ":" "_")))

(defn extract-aar-dependencies
  "Unpacks all AAR dependencies of the project into the target directory."
  [{:keys [target-path] :as project}]
  (let [deps (get-aar-dependencies project)
        aar-extracted-dir (io/file target-path "aar-extracted")]
    (debug "Extracting AAR dependencies: " deps)
    (doseq [dep deps]
      (.extractAll (ZipFile. (:file (meta dep)))
                   (str (io/file aar-extracted-dir (str-dependency dep)))))))

(defn get-aar-files
  "Returns the list of files or directories specified by `subpath` extracted
  from each AAR dependency."
  [{:keys [target-path] :as project} & subpath]
  (let [aar-extracted-dir (io/file target-path "aar-extracted")]
    (for [dep (get-aar-dependencies project)]
      (apply io/file aar-extracted-dir (str-dependency dep) subpath))))

(defn get-aar-classes
  "Returns the list of all jars extracted from all AAR dependencies."
  [project]
  (let [classes-jars (get-aar-files project "classes.jar")
        libs-dirs (get-aar-files project "libs")]
    (concat classes-jars
            (mapcat #(.listFiles ^File %) libs-dirs))))

(defn get-aar-native-paths
  "Returns the list of existing paths to native libraries extracted from AAR
  dependencies."
  [project]
  (filter #(.exists ^File %) (get-aar-files project "jni")))
