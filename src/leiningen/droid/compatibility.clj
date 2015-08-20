(ns leiningen.droid.compatibility
  "Contains utilities for letting lein-droid to cooperate with
  ant/Eclipse build tools."
  (:require [clojure.java.io :as io])
  (:use [leiningen.core
         [main :only [info]]
         [classpath :only [resolve-dependencies]]]
        [leiningen.droid.utils :only [ensure-paths]]))

(defn gather-dependencies
  "Compatibility task. Copies the dependency libraries into the libs/ folder."
  [{:keys [root] :as project} & {dir ":dir", :or {dir "libs"} :as other}]
  (println (class (first (keys other))))
  (info "Copying dependency libraries into" (str dir "..."))
  (let [destination-dir (io/file root dir)
        dependencies (resolve-dependencies :dependencies project)]
    (.mkdirs destination-dir)
    (doseq [dep dependencies]
      (io/copy dep
               (io/file destination-dir (.getName ^java.io.File dep))))))
