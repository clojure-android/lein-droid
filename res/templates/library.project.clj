(defproject {{name}}/{{name}} "0.1.0-SNAPSHOT"
  :description "FIXME: Android library description"
  :packaging "aar"

  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :plugins [[lein-droid "0.4.0-alpha4"]]
  :profiles {:default [:android-common]}

  :android {:target-version "{{target-sdk}}"
            :library true
            {{manifest-template-path}}})
