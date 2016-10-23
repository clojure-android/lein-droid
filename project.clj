(defproject lein-droid/lein-droid "0.4.6"
  :description "Plugin for easy Clojure/Android development and deployment"
  :url "https://github.com/clojure-android/lein-droid"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[robert/hooke "1.3.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [net.lingala.zip4j/zip4j "1.3.2"]
                 [com.android.tools.build/manifest-merger "24.2.3"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :resource-paths ["res"]
  :eval-in-leiningen true)
