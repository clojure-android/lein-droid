(ns leiningen.droid.new
  "Provides tasks for creating a new project or initialiaing plugin
  support in an existing one."
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:use [leiningen.core.main :only [info abort]]
        [leiningen.new.templates :only [render-text slurp-resource
                                        sanitize ->files]]
        [leiningen.droid.manifest :only [get-target-sdk-version
                                         get-project-version]]))

(defn renderer
  "Taken from lein-newnew.

  Create a renderer function that looks for mustache templates in the
  right place given the name of your template. If no data is passed, the
  file is simply slurped and the content returned unchanged."
  [name]
  (fn [template & [data]]
    (let [res (io/resource (str name "/" (sanitize template)))]
      (if data
        (render-text (slurp-resource res) data)
        (io/input-stream res)))))

(defn package-to-path [package-name]
  (string/replace package-name #"\." "/"))

(defn- load-properties
  "Loads a properties file. Returns nil if the file doesn't exist."
  [file]
  (when (.exists file)
    (with-open [rdr (io/reader file)]
      (let [properties (java.util.Properties.)]
        (.load properties rdr)
        properties))))

(defn package-name-valid? [package-name]
  (and (not (.startsWith package-name "."))
       (> (.indexOf package-name ".") -1)
       (= (.indexOf package-name "-") -1)))

(defn init
  "Creates project.clj file in an existing Android project folder.

  Presumes default directory names (like src, res and gen) and
  AndroidManifest.xml file to be already present in the project."
  [current-dir]
  (let [manifest (io/file current-dir "AndroidManifest.xml")]
    (when-not (.exists manifest)
      (abort "ERROR: AndroidManifest.xml not found - have to be in an existing"
             "Android project. Use `lein droid new` to create a new project."))
    (let [manifest-path (.getAbsolutePath manifest)
          [_ name] (re-find #".*/(.+)/\." current-dir)
          props (load-properties (io/file current-dir "project.properties"))
          data {:name name
                :version (or (get-project-version manifest-path)
                             "0.0.1-SNAPSHOT")
                :target-sdk (or (get-target-sdk-version manifest-path) "10")
                :library? (if (and props
                                   (= (.getProperty props "android.library")
                                      "true"))
                            ":library true" "")}
          render (renderer "templates")]
      (info "Creating project.clj...")
      (io/copy (render "library.project.clj" data)
               (io/file current-dir "project.clj")))))

(defn new
  "Creates new Android project given the project's name and package name."
  [project-name package-name & options]
  (when-not (package-name-valid? package-name)
    (abort "ERROR: Package name should have at least two levels and"
           "not contain hyphens (you can replace them with underscores)."))
  (let [options (apply hash-map options)
        activity (get options ":activity" "MainActivity")
        target-sdk (get options ":target-sdk" "15")
        app-name (get options ":app-name" project-name)
        data {:name project-name
              :package package-name
              :package-sanitized (sanitize package-name)
              :path (package-to-path (sanitize package-name))
              :activity activity
              :target-sdk target-sdk
              :app-name app-name}
        render (renderer "templates")]
    (->files
     data
     "assets"
     ["AndroidManifest.xml" (render "AndroidManifest.xml" data)]
     ["project.clj" (render "project.clj" data)]
     ["res/drawable-hdpi/ic_launcher.png" (render "ic_launcher_hdpi.png")]
     ["res/drawable-mdpi/ic_launcher.png" (render "ic_launcher_mdpi.png")]
     ["res/drawable-ldpi/ic_launcher.png" (render "ic_launcher_ldpi.png")]
     ["res/values/strings.xml" (render "strings.xml" data)]
     "src/java"
     ["src/clojure/{{path}}/main.clj" (render "main.clj" data)])))
