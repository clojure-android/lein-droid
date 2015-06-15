(ns {{package}}.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.threading :refer [on-ui]]))

(defactivity {{package}}.{{activity}}
  :key :main

  (onCreate [this bundle]
    (.superOnCreate this bundle)
    (on-ui
      (set-content-view! (*a)
        [:linear-layout {}
         [:text-view {:text "Hello from Clojure!"}]]))))
