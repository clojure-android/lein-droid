(defproject lein-droid/lein-droid "0.3.1-SNAPSHOT"
  :description "Plugin for easy Clojure/Android development and deployment"
  :url "https://github.com/clojure-android/lein-droid"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[robert/hooke "1.3.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["res"]
  :eval-in-leiningen true)
