(ns leiningen.droid.manifest
  "Contains functions to manipulate AndroidManifest.xml file"
  (:require [clojure.data.zip.xml :refer :all]
            [clojure.xml :as xml]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.zip :refer [up xml-zip]]
            [clostache.parser :as clostache]
            [leiningen.core.main :refer [info debug abort]]
            [leiningen.droid.aar :refer [get-aar-files]]
            [leiningen.droid.utils :refer [dev-build?]]
            [leiningen.release :refer [parse-semantic-version]])
  (:import com.android.manifmerger.ManifestMerger
           com.android.manifmerger.MergerLog
           [com.android.utils StdLogger StdLogger$Level]
           java.io.File))

;; ### Constants

;; Name of the category for the launcher activities.
(def ^{:private true} launcher-category "android.intent.category.LAUNCHER")

;; Attribute name for target SDK version.
(def ^:private target-sdk-attribute (keyword :android:targetSdkVersion))

;; Attribute name for minimal SDK version.
(def ^:private min-sdk-attribute (keyword :android:minSdkVersion))

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

;; ### Manifest parsing and data extraction

(defn get-package-name
  "Returns the name of the application's package."
  [manifest-path]
  (first (xml-> (load-manifest manifest-path) (attr :package))))

(defn get-launcher-activity
  "Returns the package-qualified name of the first activity from the
  manifest that belongs to the _launcher_ category."
  [{{:keys [manifest-path rename-manifest-package]} :android}]
  (let [manifest (load-manifest manifest-path)
        [activity-name] (some-> manifest
                            get-all-launcher-activities
                            first
                            up up
                            (xml-> (attr :android:name)))
        pkg-name (first (xml-> manifest (attr :package)))]
    (when activity-name
      (str (or rename-manifest-package pkg-name) "/"
           (if (.startsWith activity-name ".")
             (str pkg-name activity-name)
             activity-name)))))

(defn get-target-sdk-version
  "Extracts the target SDK version from the provided manifest file. If
  target SDK is not specified returns minimal SDK."
  [manifest-path]
  (let [[uses-sdk] (xml-> (load-manifest manifest-path) :uses-sdk)]
    (or (first (xml-> uses-sdk (attr target-sdk-attribute)))
        (first (xml-> uses-sdk (attr min-sdk-attribute))))))

;; ### Manifest templating

(def ^:private version-bit-sizes
  "Amount of bits allocated for each version bucket."
  [9 9 9 5])

(def ^:private version-maximums
  "Maximum values per each version bucket."
  (mapv (partial bit-shift-left 1) version-bit-sizes))

(def ^:private version-coefficients
  "Each part of the version number will be multiplied by the respective
  coefficient, all of which are calculated here."
  (->> version-bit-sizes
       (reductions +)
       (mapv (fn [offset] (bit-shift-left 1 (- 32 offset))))))

(defn- assert>
  "Asserts that a>b in version segments"
  [a b]
  (when-not (> a b)
    (abort (format "Version number segment too large to fit in the
 version-code scheme: %s > %s, maximum version in each segment is %s"
                   b a (str/join "." version-maximums))))
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

(defn merge-manifests
  "Merges the main application manifest file with manifests from AAR files."
  [{{:keys [manifest-path manifest-main-app-path]} :android :as project}]
  (let [merger (ManifestMerger. (MergerLog/wrapSdkLog
                                 (StdLogger. StdLogger$Level/VERBOSE)) nil)
        lib-manifests (get-aar-files project "AndroidManifest.xml")]
    (debug "Merging secondary manifests:" lib-manifests)
    (.process merger (jio/file manifest-path) (jio/file manifest-main-app-path)
              (into-array File lib-manifests) nil nil)))

(defn generate-manifest
  "If a :manifest-template-path is specified, perform template substitution with
  the values in :android :manifest, including the version-name and version-code
  which are automatically generated, placing the output in :manifest-path."
  [{{:keys [manifest-path manifest-template-path manifest-options manifest-main-app-path
            target-version]} :android, version :version :as project}]
  (info "Generating manifest...")
  (let [full-manifest-map (merge {:version-name version
                                  :version-code (-> version
                                                    parse-semantic-version
                                                    version-code)
                                  :target-version target-version
                                  :debug-build (dev-build? project)}
                                 manifest-options)]
    (jio/make-parents manifest-path)
    (->> full-manifest-map
         (clostache/render (slurp manifest-template-path))
         (spit manifest-main-app-path))
    (merge-manifests project)))
