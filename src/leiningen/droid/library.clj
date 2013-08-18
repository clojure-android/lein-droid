(ns leiningen.droid.library
  "Provides functions to work with library files."
  (:require [clojure.java.io :as io]
            [leiningen.core.classpath :as cpath])
  (:import java.io.File
           [java.util.zip ZipEntry ZipInputStream]))

(defn- extract-file
  "Takes a ZipInputStream and writes its current entry to the
  specified file."
  [zip-in out-filename]
  (with-open [bos (io/make-output-stream out-filename {})]
    (let [buffer (byte-array 4096)]
      (loop []
        (let [r (.read zip-in buffer)]
          (when-not (= r -1)
            (.write bos buffer 0 r)
            (recur)))))))

(defn extract-resources-from-jar
  "Takes a JAR file and extracts all files under res/ directory to
  `out-dir`."
  [jar-file out-dir]
  (let [zis (ZipInputStream. (io/input-stream jar-file))
        res-dir (str "res" File/separator)]
    (loop [found-res false]
      (if-let [entry (.getNextEntry zis)]
        (if (and (.startsWith (str entry) res-dir)
                 (not (.isDirectory entry)))
          (let [out-file (io/file out-dir (str entry))]
            (clojure.java.io/make-parents out-file)
            (extract-file zis out-file)
            (recur true))
          (recur found-res))
        (when found-res
          (str out-dir File/separator "res"))))))

(defn extract-resources-from-deps
  "Extracts resources from all project dependencies. Returns a list of
  directories to where resources were extracted."
  [{{:keys [library-res-path]} :android :as project}]
  (keep (fn [dep]
          (when (.endsWith (str dep) ".jar")
            (extract-resources-from-jar
             dep
             (io/file library-res-path
                      (second (re-matches #"(.+)\.jar" (.getName dep)))))))
        (cpath/resolve-dependencies :dependencies project)))
