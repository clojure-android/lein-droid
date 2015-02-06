(defproject {{name}}/{{name}} "{{version}}"
  :description "FIXME: Android library description"

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

  :profiles {:default [:android-common]}

  :android {:target-version "{{target-sdk}}"
            {{library?}}})
