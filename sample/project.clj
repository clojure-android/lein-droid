(defproject sample/sample "0.0.1-SNAPSHOT"
  :description "Sample Android project to test lein-droid plugin."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :middleware [leiningen.droid.utils/android-parameters]
  :min-lein-version "2.0.0"

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java" "gen"]
  ;; The following two definitions are optional. The default
  ;; target-path is "target", but you can change it to whatever you like.
  ;; :target-path "bin"
  ;; :compile-path "bin/classes"
  :aot :all  ;; This one is necessary, please keep it
  ;; :javac-options ["-g"]

  :dependencies [[android/clojure "1.4.0"]
                 [neko "1.0.0-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.0-beta6"]]}
             :release {:android {:keystore-path "/home/unlogic/private.keystore"}}}

  :android {:sdk-path "/home/unlogic/Software/android-sdk-linux_x86"
            :target-version "10"})
