(ns lein-droid.plugin
  (:require [clojure.java.io :refer [file]]
            [leiningen.core.main :refer [abort]]))

(defn middleware
  "Lein-droid's middleware adds Android SDK local repositories to :repositories.
  It has to be done in middleware because artifacts from that repositories are
  in :dependencies section, and other Leiningen tasks will crash."
  [project]
  (let [sdk-path (file (-> project :android :sdk-path))
        _ (when-not (and sdk-path (.exists sdk-path))
            (abort "SDK-path is not specified or does not exist:" sdk-path))
        p (fn [& path] {:url (str "file://" (apply file sdk-path path))})]
    (update-in project [:repositories] concat
               [["android-support" (p "extras" "android" "m2repository")]
                ["android-play-services" (p "extras" "google" "m2repository")]])))
