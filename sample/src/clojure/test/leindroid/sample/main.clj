(ns test.leindroid.sample.main
  (:use [neko.activity :only [defactivity set-content-view!]]
        [neko.notify :only [toast]]
        [neko.ui :only [make-ui]]
        [neko.threading :only [on-ui]]))

(declare ^android.app.Activity a
         ^android.widget.EditText user-input)

(defn notify-from-edit [_]
  (toast (str "Your input: " (.getText user-input))
         :long))

(defactivity test.leindroid.sample.MainActivity
  :on-create
  (fn [this bundle]
    (on-ui
     (set-content-view! this
      (make-ui [:linear-layout {:orientation :vertical
                                :layout-width :fill
                                :layout-height :wrap}
                [:edit-text {:def `user-input
                             :layout-width :fill}]
                [:button {:text "Touch me"
                          :on-click notify-from-edit}]])))))
