(defproject lein-droid/lein-droid "0.1.0-preview5"
  :description "Plugin for easy Clojure/Android development and deployment"
  :url "https://github.com/alexander-yakushev/lein-droid"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [leiningen "2.1.3"]
                 [leiningen-core "2.1.3"]
                 [robert/hooke "1.1.2"]
                 [org.clojure/data.zip "0.1.0"]
                 [com.cemerick/pomegranate "0.2.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["res"]
  :eval-in-leiningen false

  :java-source-paths ["src"]
  :javac-options     ["-target" "1.6" "-source" "1.6"]
  :warn-on-reflection true
  :aot [#"leiningen\.droid"])
