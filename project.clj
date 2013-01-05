(defproject lein-droid/lein-droid "0.1.0-beta5"
  :description "Plugin for easy Clojure/Android development and deployment"
  :url "https://github.com/alexander-yakushev/lein-droid"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[robert/hooke "1.1.2"]
                 [org.clojure/data.zip "0.1.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["res"]
  :eval-in-leiningen true)
