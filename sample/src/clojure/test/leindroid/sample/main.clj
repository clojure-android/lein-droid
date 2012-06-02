(ns test.leindroid.sample.main
  (:require [neko.repl :as repl])
  (:use [neko.activity :only [defactivity do-ui]]
        [neko.ui :only [defui]]))

(defactivity test.leindroid.sample.MainActivity)

(def activity (atom nil))

(defn init [context]
  (repl/try-start-repl context :port 9999))

(defn -onCreate [this bundle]
  (init this)
  (reset! activity this)
  (.superOnCreate this bundle)
  (do-ui @activity
       (. @activity setContentView
          (defui [:linear-layout {:id ::main-layout,
                                  :orientation :vertical,
                                  :layout-width :fill, :layout-height :wrap}
                  [:button {:id ::like
                            :text "Android",
                            :layout-width :fill, :layout-height :wrap}]
                  [:button {:text "Clojure",
                            :layout-width :fill, :layout-height :wrap}]]))))