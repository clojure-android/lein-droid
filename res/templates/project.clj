(defproject {{name}}/{{name}} "0.0.1-SNAPSHOT"
  :description "FIXME: Android project description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :plugins [[lein-droid "0.3.2"]]

  :dependencies [[org.clojure-android/clojure "1.7.0-alpha3" :use-resources true]
                 [neko/neko "3.1.1"]]
  :profiles {:default [:dev]

             :dev
             [:android-common :android-user
              {:dependencies [[org.clojure-android/tools.nrepl "0.2.6"]]
               :target-path "target/debug"
               :android {:aot :all-with-unused
                         :rename-manifest-package "{{package-sanitized}}.debug"
                         :manifest-options {:app-name "{{app-name}} - debug"}}}]
             :release
             [:android-common
              {:target-path "target/release"
               :android
               { ;; Specify the path to your private keystore
                ;; and the the alias of the key you want to
                ;; sign APKs with.
                ;; :keystore-path "/home/user/.android/private.keystore"
                ;; :key-alias "mykeyalias"

                :ignore-log-priority [:debug :verbose]
                :aot :all
                :build-type :release}}]}

  :android {;; Specify the path to the Android SDK directory.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; Try increasing this value if dexer fails with
            ;; OutOfMemoryException. Set the value according to your
            ;; available RAM.
            :dex-opts ["-JXmx4096M"]

            ;; If previous option didn't work, uncomment this as well.
            ;; :force-dex-optimize true

            :target-version "{{target-sdk}}"
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"
                             "cljs-tooling.complete" "cljs-tooling.info"
                             "cljs-tooling.util.analysis" "cljs-tooling.util.misc"
                             "cider.nrepl" "cider-nrepl.plugin"]})
