(ns test.leindroid.sample.main
  (:use [neko.activity :only [defactivity set-content-view!]]
        [neko.notify :only [toast]]
        [neko.ui :only [make-ui]]
        [neko.threading :only [on-ui]]
        [neko.application :only [defapplication]]))

(declare ^android.app.Activity a
         ^android.widget.EditText user-input)

;; This line defines the Application class and automatically
;; initializies neko and nREPL.
(defapplication test.leindroid.sample.Application)

(defn notify-from-edit [_]
  (toast (str "Your input: " (.getText user-input))
         :long))

(defactivity test.leindroid.sample.MainActivity
  :def a
  :create
  (fn [this bundle]
    (on-ui
     (set-content-view! a
      (make-ui [:linear-layout {:orientation :vertical
                                :layout-width :fill
                                :layout-height :wrap}
                [:edit {:def user-input
                        :layout-width :fill}]
                [:button {:text "Touch me"
                          :on-click notify-from-edit}]])))))
