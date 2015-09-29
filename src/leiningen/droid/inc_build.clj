(ns leiningen.droid.inc-build
  "Subtasks related to build process as a whole. Decides what to run and
   what not to. It stores the subtasks and files dependencies as function."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [leiningen.core.main :refer [info]]))

(defn file-modified?
  "Check if input file is modified, since the last time
   recorded. Return false iff recorded timestamp is same as the one
   that's read recently."
  [input-file timestamp-file]
  (if (not (.exists timestamp-file))
    true
    (let [input-file-time (.lastModified input-file)
          input-file-name (str input-file)
          timestamps (edn/read-string (slurp timestamp-file))]
      (if (and (contains? timestamps input-file-name)
        (== (timestamps input-file-name) input-file-time))
        false
        true))))

(defn input-modified?
  "Check if some of the given input files are modified. Return true if
   at least one file has been modified."
  [input-files]
  (some file-modified? input-files))

(defn generate-build-constants-deps
  "Get the file dependencies for code-gen/generate-build-constants subtask."
  []
  (println (input-modified?))
  ["project.clj"])
