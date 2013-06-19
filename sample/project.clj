(defproject sample/sample "0.0.1-SNAPSHOT"
  :description "Sample Android project to test lein-droid plugin."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"

  :warn-on-reflection true

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java" "gen"]
  ;; The following two definitions are optional. The default
  ;; target-path is "target", but you can change it to whatever you like.
  ;; :target-path "bin"
  ;; :compile-path "bin/classes"

  ;; Uncomment this line if your project doesn't use Clojure. Also
  ;; don't forget to remove respective dependencies.
  ;; :java-only true

  :dependencies [[android/clojure "1.4.0"]
                 [neko/neko "2.0.0-beta3"]]
  :profiles {:dev {:dependencies [[android/tools.nrepl "0.2.0-bigstack"]]
                   :android {:aot :all-with-unused}}
             :release {:android {;; Specify the path to your private
                                 ;; keystore and the the alias of the
                                 ;; key you want to sign APKs with.
                                 ;; :keystore-path "/home/user/.android/private.keystore"
                                 ;; :key-alias "mykeyalias"

                                 ;; You can specify these to avoid
                                 ;; entering them for each rebuild,
                                 ;; but generally it's a bad idea.
                                 ;; :keypass "android"
                                 ;; :storepass "android"

                                 :aot :all}}}

  :android {;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; Specify this if your project is a library.
            ;; :library false

            ;; Uncomment this if dexer fails with OutOfMemoryException.
            ;; :force-dex-optimize true

            ;; Options to pass to dx executable, former is for general
            ;; java-related options and later is for 'dex task'-specific options.
            ;; :dex-opts ["-JXmx4096M"]
            ;; :dex-aux-opts ["--num-threads=2"]

            ;; Proguard config for "droid create-obfuscated-dex" task.
            ;; :proguard-conf-path "proguard.cfg"
            ;; :proguard-opts ["-printseeds"]

            ;; Uncomment this line to be able to use Google API.
            ;; :use-google-api true

            ;; Use this property to add project dependencies.
            ;; :project-dependencies [ "/path/to/library/project" ]

            ;; Sequence of external jars to "link" against, e.g. android-support-v4.jar
            ;; :external-classes-paths ["<sdk-home>/extras/android/support/v4/android-support-v4.jar"]

            ;; sequence of paths where native libraries may be found for packaging
            ;; :native-libraries-paths ["libs"]

            ;; Target version affects api used for compilation.
            :target-version "10"

            ;; Minimum supported version could be specified as well,
            ;; its meaning is similar to that in AndroidManifest.xml.
            ;; :min-version "10"

            ;; Sequence of namespaces that should not be compiled.
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"]

            })
