(ns leiningen.droid.library
  "Provides functions to work with library files."
  (:require [clojure.java.io :as io]
            [leiningen.core.classpath :as cpath]
            leiningen.jar
            [leiningen.droid.utils :refer [read-binary-file relativize-path]])
  (:import java.io.File
           [java.util.zip ZipEntry ZipInputStream]))

;; ## Resource packing

(defn make-res-filespec
  "Terrible crutch to make Leiningen package resource under res/
  folder without unwinding."
  [res-dir]
  (for [f (file-seq res-dir)
        :when (.isFile f)]
    {:type :bytes
     :bytes (.toByteArray
             (with-open [in (io/input-stream f)
                         out (java.io.ByteArrayOutputStream.)]
              (read-binary-file in out)))
     :path (str (io/file "res" (relativize-path res-dir f)))}))

(defn filespecs-hook
  "Takes original `leiningen.jar/filespecs` function and appends
  resource specs to its result."
  [f project]
  (let [fspec (f project)]
    (concat fspec (make-res-filespec (io/file (:root project) "res")))))

(robert.hooke/add-hook #'leiningen.jar/filespecs #'filespecs-hook)

;; ## Resource extraction

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
