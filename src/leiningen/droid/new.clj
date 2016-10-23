(ns leiningen.droid.new
  "Provides tasks for creating a new project or initialiaing plugin
  support in an existing one."
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.core.main :refer [info abort]]
            [leiningen.core.project :as project]
            [leiningen.droid.manifest :refer [get-target-sdk-version]]
            [leiningen.droid.utils :refer [get-dependencies]]
            [leiningen.new.templates :refer [render-text slurp-resource
                                             sanitize ->files]]))

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

(defn package-name-valid? [package-name]
  (and (not (.startsWith package-name "."))
       (> (.indexOf package-name ".") -1)
       (= (.indexOf package-name "-") -1)))

(defn- latest-version
  "Downloads the latest version of the given artifact symbol, and returns the
  version string."
  [artifact default]
  (let [version (try (->> (get-dependencies
                           {:dependencies [[artifact "RELEASE"]]
                            :repositories project/default-repositories})
                          keys
                          (some #(when (= (first %) artifact) %))
                          second)
                     (catch Exception ex nil))]
    (if version
      (do (info "Found" artifact version)
          version)
      (do (info "Couldn't resolve the latest" artifact
                "version, using default" default)
          default))))

(defn- current-plugin-version
  "Returns the version of this very lein-droid plugin currently being run."
  []
  (try (->> (io/resource "META-INF/maven/lein-droid/lein-droid/pom.properties")
            io/reader line-seq
            (keep #(second (re-matches #"^version=(.+)" %)))
            (some identity))
       (catch Exception ex nil)))

(defn init
  "Creates project.clj file within an existing Android library folder.

  Presumes default directory names (like src, res and gen) and
  AndroidManifest.xml file to be already present in the project."
  [current-dir]
  (let [manifest (io/file current-dir "AndroidManifest.xml")]
    (when-not (.exists manifest)
      (abort "ERROR: AndroidManifest.xml not found - have to be in an existing"
             "Android project. Use `lein droid new` to create a new project."))
    (let [manifest-path (.getAbsolutePath manifest)
          data {:name (.getName (io/file current-dir))
                :target-sdk (or (get-target-sdk-version manifest-path) "15")
                :lein-droid-version (or (current-plugin-version) "0.4.3")}
          render (renderer "templates")]
      (info "Creating project.clj...")
      (io/copy (render "library.project.clj" data)
               (io/file current-dir "project.clj")))))

(defn new-library
  "Creates new Android library."
  [library-name package-name data]
  (let [render (renderer "templates")]
    (info "Creating library" library-name "...")
    (->files
     data
     "assets"
     [".gitignore" (render "gitignore")]
     ["LICENSE" (render "LICENSE")]
     ["README.md" (render "README.library.md" data)]
     ["AndroidManifest.template.xml" (render "AndroidManifest.library.xml" data)]
     ["project.clj" (render "library.project.clj" data)]
     ["res/values/strings.xml" (render "strings.library.xml" data)]
     ["src/java/{{path}}/Util.java" (render "Util.java" data)]
     ["src/clojure/{{path}}/main.clj" (render "core.clj" data)])))

(defn new-application
  "Creates new Android application."
  [project-name package-name data]
  (let [render (renderer "templates")]
    (info "Creating project" project-name "...")
    (->files
     data
     "assets"
     [".gitignore" (render "gitignore")]
     ["LICENSE" (render "LICENSE" data)]
     ["README.md" (render "README.md" data)]
     ["AndroidManifest.template.xml" (render "AndroidManifest.template.xml" data)]
     ["project.clj" (render "project.clj" data)]
     ["build/proguard-minify.cfg" (render "proguard_minify.cfg" data)]
     ["build/proguard-multi-dex.cfg" (render "proguard_multi_dex.cfg" data)]
     ["res/drawable-hdpi/splash_circle.png" (render "splash_circle.png")]
     ["res/drawable-hdpi/splash_droid.png" (render "splash_droid.png")]
     ["res/drawable-hdpi/splash_hands.png" (render "splash_hands.png")]
     ["res/drawable-hdpi/ic_launcher.png" (render "ic_launcher_hdpi.png")]
     ["res/drawable-mdpi/ic_launcher.png" (render "ic_launcher_mdpi.png")]
     ["res/drawable/splash_background.xml" (render "splash_background.xml")]
     ["res/anim/splash_rotation.xml" (render "splash_rotation.xml")]
     ["res/layout/splashscreen.xml" (render "splashscreen.xml")]
     ["res/values/strings.xml" (render "strings.xml" data)]
     ["src/java/{{path}}/SplashActivity.java" (render "SplashActivity.java" data)]
     ["src/clojure/{{path}}/main.clj" (render "main.clj" data)])))

(defn new
  "Creates new Android project given the project's name and package name."
  [project-name package-name & options]
  (when-not (package-name-valid? package-name)
    (abort "ERROR: Package name should have at least two levels and"
           "not contain hyphens (you can replace them with underscores)."))
  (info "Resolving latest artifact versions...")
  (let [options (apply hash-map options)
        data {:name project-name
              :package package-name
              :package-sanitized (sanitize package-name)
              :path (package-to-path (sanitize package-name))
              :activity (get options ":activity" "MainActivity")
              :target-sdk (get options ":target-sdk" "18")
              :min-sdk (get options ":min-sdk" "15")
              :app-name (get options ":app-name" project-name)
              :library (get options ":library" false)
              :new-project true
              :lein-droid-version (or (current-plugin-version) "0.4.3")
              :clojure-version (latest-version 'org.clojure-android/clojure "1.7.0-r3")
              :neko-version (latest-version 'neko "4.0.0-alpha5")
              :skummet-version (latest-version 'org.skummet/clojure "1.7.0-r1")}]
    (if (= (:library data) "true")
      (new-library project-name package-name data)
      (new-application project-name package-name data))))
