(ns test.leindroid.sample.main
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.debug :refer [*a]]
            [neko.notify :refer [toast]]
            [neko.resource :as res]
            [neko.find-view :refer [find-view]]
            [neko.threading :refer [on-ui]])
  (:import android.widget.TextView))

(res/import-all)

(defn notify-from-edit [activity]
  (let [^TextView input (.getText (find-view activity ::user-input))]
    (toast activity
           (if (empty? input)
             "Your input is empty"
             (str "Your input: " input))
           :long)))

(defactivity test.leindroid.sample.MainActivity
  :key :main

  (onCreate [this bundle]
    (.superOnCreate this bundle)
    (neko.debug/keep-screen-on this)
    (on-ui
      (set-content-view! (*a)
        [:linear-layout {:orientation :vertical
                         :layout-width :fill
                         :layout-height :wrap}
         [:edit-text {:id ::user-input
                      :layout-width :fill}]
         [:button {:text "Touch me"
                   :on-click (fn [_] (notify-from-edit (*a)))}]]))))
