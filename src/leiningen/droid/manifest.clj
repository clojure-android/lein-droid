(ns leiningen.droid.manifest
  "Contains functions to manipulate AndroidManifest.xml file"
  (:require [clojure.data.zip.xml :refer :all]
            [clojure.xml :as xml]
            [clojure.java.io :as jio]
            [clojure.zip :refer [append-child node up xml-zip]]
            [clostache.parser :as clostache]
            [leiningen.core.main :refer [info]]
            [leiningen.release :refer [parse-semantic-version]])
  (:import (java.io FileWriter)))

;; ### Constants

;; Name of the category for the launcher activities.
(def ^{:private true} launcher-category "android.intent.category.LAUNCHER")

;; Name of the Internet permission.
(def ^{:private true} internet-permission "android.permission.INTERNET")

;; XML tag of the Internet permission.
(def ^{:private true} internet-permission-tag
  {:tag :uses-permission
   :attrs {(keyword :android:name) internet-permission}})

;; Attribute name for target SDK version.
(def ^:private target-sdk-attribute (keyword :android:targetSdkVersion))

;; Attribute name for minimal SDK version.
(def ^:private min-sdk-attribute (keyword :android:minSdkVersion))

;; Attribute name for project version name.
(def ^:private version-name-attribute (keyword :android:versionName))

;; ### Local functions

(defn- load-manifest
  "Parses given XML manifest file and creates a zipper from it."
  [manifest-path]
  (xml-zip (xml/parse manifest-path)))

(defn- get-all-launcher-activities
  "Returns a list of zipper trees of Activities which belong to the
  _launcher_ category."
  [manifest]
  (xml-> manifest :application :activity :intent-filter :category
         (attr= :android:name launcher-category)))

(defn- has-internet-permission?
  "Checks if manifest contains Internet permission."
  [manifest]
  (first (xml-> manifest
                :uses-permission (attr= :android:name internet-permission))))

(defn- write-manifest
  "Writes the manifest to the specified filename."
  [manifest filename]
  (binding [*out* (FileWriter. filename)]
    (xml/emit (node manifest))))

;; ### Public functions

(defn get-package-name
  "Returns the name of the application's package."
  [manifest-path]
  (first (xml-> (load-manifest manifest-path) (attr :package))))

(defn get-launcher-activity
  "Returns the package-qualified name of the first activity from the
  manifest that belongs to the _launcher_ category."
  [manifest-path]
  (let [manifest (load-manifest manifest-path)
        [activity-name] (some-> manifest
                            get-all-launcher-activities
                            first
                            up up
                            (xml-> (attr :android:name)))
        pkg-name (first (xml-> manifest (attr :package)))]
    (when activity-name (str pkg-name "/" activity-name))))

(defn write-manifest-with-internet-permission
  "Updates the manifest on disk guaranteed to have the Internet permission."
  [manifest-path]
  (let [manifest (load-manifest manifest-path)]
   (write-manifest (if (has-internet-permission? manifest)
                     manifest
                     (append-child manifest internet-permission-tag))
                   manifest-path)))

(defn get-target-sdk-version
  "Extracts the target SDK version from the provided manifest file. If
  target SDK is not specified returns minimal SDK."
  [manifest-path]
  (let [[uses-sdk] (xml-> (load-manifest manifest-path) :uses-sdk)
        [target-sdk] (xml-> uses-sdk (attr target-sdk-attribute))]
    (or target-sdk
        (first (xml-> uses-sdk (attr min-sdk-attribute))))))

(defn get-project-version
  "Extracts the project version name from the provided manifest file."
  [manifest-path]
  (first (xml-> (load-manifest manifest-path) (attr version-name-attribute))))

(def ^:private version-bit-sizes    [9 9 9 5])
(def ^:private version-maximums     (mapv (partial bit-shift-left 1) version-bit-sizes))
(def ^:private version-coefficients (mapv (fn [offset] (bit-shift-left 1 (- 32 offset))) (reductions + version-bit-sizes)))

(defn- assert>
  "Asserts that a>b in version segments"
  [a b]
  (assert (> a b) (str "Version number segment too large to fit in the
  version-code scheme " b ">" a ", maximum version in each segment
  is " (clojure.string/join "." version-maximums)))
  b)

(defn version-code
  "Given a version map containing :major :minor :patch
   :build and :priority version numbers, returns an integer which is
   guaranteed to be greater for semantically larger version numbers.

   Splitting the 32 bit version code into 5 segments such that each
   semantically greater version will have a larger version code. The
   segments represent major, minor, patch, build and package
   priority (multiple builds of the same android apk where one takes
   precedence over another, for instance in the case where higher
   resolution assets are available, but a fallback is made available
   for devices which do not support the configuration).

   Largest possible version number: v512.512.512 (32)"
  [version-map]
  (->> version-map
       ((juxt :major :minor :patch :priority))
       (map (fnil assert> 0 0) version-maximums)
       (map * version-coefficients)
       (reduce +)))

(defn generate-manifest
  "If a :manifest-template-file is specified, perform template substitution with
  the values in :android :manifest, including the version-name and version-code
  which are automatically generated, placing the output in :manifest-path."
  [{{:keys [manifest-path manifest-template-path manifest-options target-path
            build-type]} :android, version :version :as project}]
  (info "Generating manifest...")
  (let [full-manifest-map (merge {:version-name version
                                  :version-code (-> version
                                                    parse-semantic-version
                                                    version-code)
                                  :debug-build (not build-type)}
                                 manifest-options)]
    (when (.exists (jio/file manifest-template-path))
      (clojure.java.io/make-parents manifest-path)
      (->> full-manifest-map
           (clostache/render (slurp manifest-template-path))
           (spit manifest-path)))))
