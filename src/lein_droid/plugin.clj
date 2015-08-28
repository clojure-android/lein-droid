(ns lein-droid.plugin
  "Hosts middleware function to be applied early to the project map."
  (:require [leiningen.droid.classpath :refer [init-hooks]]
            [leiningen.droid.utils :refer [android-parameters]]))

(defn middleware
  "Lein-droid's middleware adds default Android parameters to `:android` map,
  and also adds local Maven repositories from Android SDK."
  [project]
  (init-hooks)
  (android-parameters project))
