;; Provides utilities for the plugin.
;;
(ns leiningen.droid.utils
  (:use [leiningen.core.project :only (absolutize-paths)]))

(defn get-sdk-platform-path
  "Returns a version-specific path to the Android platform tools."
  [sdk-root version]
  (format "%s/platforms/android-%s" sdk-root version))

(defn get-sdk-android-jar [sdk-root version]
  (str (get-sdk-platform-path sdk-root version) "/android.jar"))

(defn add-android-default-paths [project]
  (absolutize-paths
   (assoc project
     :source-paths ["src/clojure"]
     :java-source-paths ["src/java" "gen"]
     :target-path "bin"
     :compile-path "bin/classes"
     :android-out-dex-path "bin/classes.dex"
     :android-manifest-path "AndroidManifest.xml"
     :android-res-path "res"
     :android-out-res-path "bin/res"
     :android-assets-path "assets"
     :android-out-res-pkg-path (str "bin/" (:name project) ".ap_")
     :android-out-apk-path (str "bin/" (:name project) ".apk")
     :android-debug-keystore-path (str (System/getenv "HOME")
                                       "/.android/debug.keystore"))))
