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
  :aot :all-with-unused ;; This one is necessary, please keep it
  :aot-exclude-ns ["clojure.parallel"]

  :dependencies [[android/clojure "1.4.0"]
                 [neko/neko "1.1.1-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.0-beta6"]]}
             :release {:android {;; Specify the path to your private
                                 ;; keystore and the the alias of the
                                 ;; key you want to sign APKs with.
                                 ;; :keystore-path "/home/user/.android/private.keystore"
                                 ;; :key-alias "mykeyalias"
                                 }
                       :aot :all}}

  :android {;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"
            :target-version "10"})
