(defproject {{name}}/{{name}} "{{version}}"
  :description "FIXME: Android library description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java" "gen"]

  :dependencies [[android/clojure "1.4.0"]
                 [neko/neko "2.0.0-beta1"]]

  :android {:target-version "{{target-sdk}}"
            {{library?}}})
