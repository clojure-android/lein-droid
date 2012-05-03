(ns leiningen.droid.manifest
  "Contains functions to manipulate AndroidManifest.xml file"
  (:require [clojure.xml :as xml])
  (:use [clojure.zip :only (xml-zip up)]
        [clojure.data.zip.xml]))

;; ### Constants

;; Name of the category for the launcher activities.
(def ^{:private true} launcher-category "android.intent.category.LAUNCHER")

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

;; ### Public functions

(defn get-launcher-activity
  "Return the package name and the name of the first activity from the
  manifest that belongs to the _launcher_ category."
  [manifest-path]
  (let [manifest (load-manifest manifest-path)
        [package-name] (xml-> manifest (attr :package))]
    (str package-name "/."
     (-> manifest
         get-all-launcher-activities
         first
         up up
         (xml-> (attr :android:name))
         first))))

