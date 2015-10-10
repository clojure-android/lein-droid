(ns leiningen.droid.inc-build
  "Subtasks related to build process as a whole. Decides what to run and
   what not to. It stores the subtasks and files dependencies as function."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [leiningen.core.main :refer [info debug]]))

(defn get-subtask-dependencies
  "Get the file dependencies for subtasks. This is a static class returning a map
   of subtask names and corresponding file/directory dependency in a vector. The
   key name has to be the exact subtask name. The dependency paths are relative
   (to the project)."
  [project]
  {"generate-manifest" ["project.clj"]
   "generate-resource-code" ["res", "target/debug/AndroidManifest.xml"]
   "generate-build-constants" ["project.clj"]
   "compile" (distinct (project :source-paths))
   "create-dex" []})

(defn- walk
  "Walk the given directory searching for files. For now we search all the files
   irrespective of the type (extension i.e. java or clj or xml)."
  [dirpath]
  (doall (filter #(not (.isDirectory %)) (file-seq dirpath))))

(defn- latest-timestamp-in-directory
  "Find the timestamp of last modified file in a directory.
   Recursively walk the directories and find timestamp of
   file that was recently modified."
  [dir]
  (apply max (map #(.lastModified %) (walk dir))))

(defn- file-modified?
  "Check if input file is modified, since the last time
   recorded. Return false iff recorded timestamp is same as the one
   that's read recently."
  [timestamp-file subtask input-file]
  (if (not (.exists timestamp-file))
    true
    (let [input-file-time (if (.isDirectory input-file)
                            (latest-timestamp-in-directory input-file)
                            (.lastModified input-file))
          input-file-name (str input-file)
          timestamps (edn/read-string (slurp timestamp-file))
          recorded-time (get-in timestamps [subtask input-file-name])]
      (if (and recorded-time (== recorded-time input-file-time))
        false
        true))))

(defn- partial-file-modified?
  "Partial file-modified? function which takes the timestamp-file."
  [timestamp-file subtask]
  (partial file-modified? timestamp-file subtask))

(defn input-modified?
  "Check if some of the given input files/files in dirs are modified. Return true if
   at least one file has been modified."
  [timestamp-file-name subtask input-path-names]
  (let [input-paths (map io/file input-path-names)
        timestamp-file (io/file timestamp-file-name)]
    (some (partial-file-modified? timestamp-file subtask) input-paths)))

(defn- write-timestamp
  "First read the current timestamp (for files for subtasks) if exists and update
   the timestamps in."
  [timestamp-file-name subtask path]
  (let [timestamp-file (io/file timestamp-file-name)
        path-timestamp (if (.isDirectory path)
                         (latest-timestamp-in-directory path)
                         (.lastModified path))]
    (debug "Path is" path "timestamp is" path-timestamp)
    (if (not (.exists timestamp-file))
      (spit timestamp-file-name (prn-str {(str subtask) {(str path) path-timestamp}}))
      (let [timestamps (edn/read-string (slurp timestamp-file))
            updated-timestamps (update-in timestamps [subtask] assoc (str path) path-timestamp)]
        (spit timestamp-file-name (prn-str updated-timestamps))))))

(defn record-timestamps
  "After execution of each subtask, record the timestamps of the files/directories,
   that found modified. If it's a directory then record the timestamp of the recently
   modified file. It recursively traverses directory structure to get the recent timestamp."
  [timestamp-file-name subtask path-names]
  (let [paths (map io/file path-names)]
    (debug "Recording timestamps for" subtask paths)
    (doall (map (partial write-timestamp timestamp-file-name subtask) paths))))
