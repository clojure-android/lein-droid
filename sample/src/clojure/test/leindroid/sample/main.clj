(ns test.leindroid.sample.main
  (:import android.widget.TextView))

(gen-class
 :name test.leindroid.sample.MainActivity
 :main false
 :extends android.app.Activity
 :overrides-methods ["onCreate"]
 :exposes-methods {onCreate superOnCreate})

(defn -onCreate [^test.leindroid.sample.MainActivity this bundle]
  (.superOnCreate this bundle)
  (let [tv (TextView. this)]
    (.setText tv (str "Hello " "Skummet!"))
    (.setContentView this tv))
  )
