(ns {{package}}.{{activity}}
  (:import [android.widget TextView LinearLayout])
  (:require [neko.compilation :as neko]
            [neko.repl :as repl])
  (:gen-class
   :main false
   :extends android.app.Activity
   :exposes-methods {onCreate superOnCreate}))

(defn init [context]
  (neko/init context "classes")
  (repl/try-start-repl))

(defn -onCreate [this bundle]
  (init this)
  (let [hello_text (TextView. this)
        layout (LinearLayout. this)]
    (.setText hello_text "Hello Android from Clojure!")
    (doto this
      (.superOnCreate bundle)
      (.setContentView layout))
    (.addView layout hello_text)))