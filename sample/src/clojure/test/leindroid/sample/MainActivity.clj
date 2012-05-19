(ns test.leindroid.sample.MainActivity
  (:import android.util.Log [test.leindroid.sample R$layout R$id]
           [android.view View$OnClickListener])
  (:require [neko.compilation :as neko]
            [neko.repl :as repl])
  (:gen-class
   :main false
   :extends android.app.Activity
   :exposes-methods {onCreate superOnCreate}))

(def uitems (atom {}))

(defn init [context]
  (neko/init context "classes")
  (repl/try-start-repl :port 9999))

(defn -onCreate [this bundle]
  (swap! uitems #(assoc % :activity this))
  (init this)
  (doto this
    (.superOnCreate bundle)
    (.setContentView (R$layout/main)))
  (let [butt (. this findViewById R$id/butt)]
    (swap! uitems #(assoc % :butt butt))
    (.setOnClickListener butt (proxy [View$OnClickListener] []
                                (onClick [view]
                                  (Log/d "CLOJURE" (str "Compilation works: " (eval '(+ 20 22)))))))))

