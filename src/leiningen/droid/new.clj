(ns leiningen.droid.new
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:use [leiningen.new.templates :only [render-text slurp-resource
                                        sanitize ->files]]))

(defn- render
  [template-name & [data]]
  (let [file (io/file "templates" template-name)]
    (if data
     (render-text (slurp file) data)
     file)))

(defn package-to-path [package-name]
  (string/replace package-name #"\." "/"))

(defn new
  "Creates new Android project given the project's name and package name."
  [project-name package-name & {:keys [activity min-sdk app-name],
                                :or {activity "MainActivity", min-sdk "10",
                                     app-name name}}]
  (let [data {:name project-name
              :package package-name
              :package-sanitized (sanitize package-name)
              :path (package-to-path (sanitize package-name))
              :activity activity
              :min-sdk min-sdk
              :app-name app-name}]
    (->files
     data
     "assets"
     ["AndroidManifest.xml" (render "AndroidManifest.xml" data)]
     ["project.clj" (render "project.clj" data)]
     ["res/drawable-hdpi/ic_launcher.png" (render "ic_launcher_hdpi.png")]
     ["res/drawable-mdpi/ic_launcher.png" (render "ic_launcher_mdpi.png")]
     ["res/drawable-ldpi/ic_launcher.png" (render "ic_launcher_ldpi.png")]
     ["res/values/strings.xml" (render "strings.xml")]
     "src/java"
     ["src/clojure/{{path}}/{{activity}}.clj"
      (render "MainActivity.clj" data)])))
