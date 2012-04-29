;; Provides utilities for the plugin.
;;
(ns leiningen.droid.utils)

(defn get-sdk-platform-path
  "Returns a version-specific path to the Android platform tools."
  [sdk-root version]
  (format "%s/platforms/android-%s" sdk-root version))