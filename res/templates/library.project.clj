(defproject {{name}}/{{name}} "{{version}}"
  :description "FIXME: Android library description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]

  :dependencies [[org.clojure-android/clojure "1.5.1-jb"]
                 [neko/neko "3.0.0-preview3"]]

  :android {:target-version "{{target-sdk}}"
            {{library?}}})
