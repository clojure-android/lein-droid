(ns test.leindroid.sample.main
  (:use [neko.activity :only [defactivity do-ui set-content-view!]]
        [neko.notify :only [toast]]
        [neko.ui :only [defui by-id]]
        [neko.application :only [defapplication]]))

;; This line defines the Application class and automatically
;; initializies neko and nREPL.
(defapplication test.leindroid.sample.Application)

(defn notify-from-edit [_]
  (.show
   (toast (str "Your input: "
               (.getText (by-id ::user-input)))
          :long)))

(defactivity test.leindroid.sample.MainActivity
  :create
  (fn [this bundle]
    (do-ui
     MainActivity
     (set-content-view!
      (defui [:linear-layout {:orientation :vertical
                              :layout-width :fill
                              :layout-height :wrap}
              [:edit {:id ::user-input
                      :layout-width :fill}]
              [:button {:text "Touch me"
                        :on-click notify-from-edit}]])))))
