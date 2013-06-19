(ns leiningen.droid.manifest
  "Contains functions to manipulate AndroidManifest.xml file"
  (:require [clojure.xml :as xml])
  (:use [clojure.zip :only (xml-zip up node append-child)]
        [clojure.data.zip.xml])
  (:import java.io.FileWriter))

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
        [activity-name] (-> manifest
                            get-all-launcher-activities
                            first
                            up up
                            (xml-> (attr :android:name)))
        pkg-name (first (xml-> manifest (attr :package)))]
    (if (.startsWith activity-name pkg-name)
      activity-name
      (str pkg-name "/" activity-name))))

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
