(defproject sample/sample "0.0.1-SNAPSHOT"
  :description "Sample Android project to test lein-droid plugin."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]
  :clean-targets ["target" "gen"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

  ;; Uncomment this line if your project doesn't use Clojure. Also
  ;; don't forget to remove respective dependencies.
  ;; :java-only true

  :aliases {"doall-trial" ["with-profile" "trial-version-dev" "do" ["droid" "build"] ["droid" "apk"] ["droid" "deploy"]]}
  
  :dependencies [[org.clojure-android/clojure "1.6.0-RC1" :use-resources true]
                 [neko/neko "3.0.1"]]
  :profiles {:android-dev [:android-config ; :android-config should be
                                           ; specified in your
                                           ; .lein/profiles.clj and
                                           ; contain machine-specific
                                           ; options such as
                                           ; {:android {:sdk-path ""}}
                           {:dependencies [[org.clojure/tools.nrepl "0.2.3"]
                                           [compliment "0.0.3"]]
                            :android {:aot :all-with-unused

                                      ;; The namespace of the app
                                      ;; package - having a different
                                      ;; one for dev and release allows
                                      ;; you to install both at the
                                      ;; same time
                                      :apk-package-name "my.sample.app.dev"}}]
             :android-release [:android-config
                               {:android { ;; Specify the path to your private
                                          ;; keystore and the the alias of the
                                          ;; key you want to sign APKs with.
                                          ;; :keystore-path "/home/user/.android/private.keystore"
                                          ;; :key-alias "mykeyalias"
                                          ;; :sigalg "MD5withRSA"

                                          ;; You can specify these to avoid
                                          ;; entering them for each rebuild,
                                          ;; but generally it's a bad idea.
                                          ;; :keypass "android"
                                          ;; :storepass "android"

                                          :ignore-log-priority [:debug :verbose]
                                          :aot :all

                                          ;; This tells lein-droid to
                                          ;; build in release mode,
                                          ;; disabling debugging and
                                          ;; signing the resulting
                                          ;; package
                                          :build-type :release}}]

             ;; Here's an example of using different profiles
             :trial-version-dev [:android-config
                                 :android-dev
                                 {:android {:apk-package-name "my.sample.app.dev.trial"
                                            ;And then some options which might be
                                            ;additional/different source-paths to pull in different
                                            ;code, or a manifest option which configures some aspect
                                            ;of your application
                                            }}]}

  :android { ;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; Use this if you don't want to use the latest version of
            ;; Android Build Tools.
            ;; :build-tools-version "19.0.3"

            ;; Specify this if your project is a library.
            ;; :library true

            ;; Uncomment this if dexer fails with OutOfMemoryException.
            ;; :force-dex-optimize true

            ;; Options to pass to dx executable.
            ;; :dex-opts ["-JXmx4096M"]

            ;; Proguard config for "droid create-obfuscated-dex" task.
            ;; :proguard-conf-path "proguard.cfg"
            ;; :proguard-opts ["-printseeds"]

            ;; Uncomment this line to be able to use Google API.
            ;; :use-google-api true

            ;; This option allows you to specify Android support
            ;; libraries you want to use in your application.
            ;; Available versions: "v4", "v7-appcompat",
            ;; "v7-gridlayout", "v7-mediarouter", "v13".
            ;; :support-libraries ["v7-appcompat" "v13"]

            ;; Use this property to add project dependencies.
            ;; :project-dependencies [ "/path/to/library/project" ]

            ;; Sequence of external jars or class folders to include
            ;; into project.
            ;; :external-classes-paths ["path/to/external/jar/file"
            ;;                          "path/to/classfiles/"]

            ;; Sequence of jars, resources from which will be added to
            ;; application package.
            ;; :resource-jars-paths ["path/to/resource/jar"]

            ;; Sequence of native libraries files that will be added
            ;; to application package.
            ;; :native-libraries-paths ["path/to/native/library"]

            ;; Target version affects api used for compilation.
            :target-version :jelly-bean

            ;; Sequence of namespaces that should not be compiled.
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"]

            ;; This specifies replacements which are inserted into
            ;; AndroidManifest.xml at build time. See cloustache for
            ;; more advanced substitution syntax. Version name and
            ;; code are automatically inserted
            :manifest {:manifest-package "my.sample.app"
                       :app-name         "Sample App"}
            })
