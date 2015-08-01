(ns leiningen.droid.test
  (:refer-clojure :exclude [test])
  (:require [bultitude.core :as b]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [leiningen.core.classpath :as cp]
            [leiningen.droid.code-gen :as code-gen]
            [leiningen.droid.compile :as compile]
            [leiningen.droid.utils :as utils]))

(defn local-test
  "Runs tests locally using Robolectric."
  [{{:keys [cloverage-exclude-ns]} :android :as project} & [mode]]
  (when-not (-> project :android :library)
    (code-gen/code-gen project))
  (compile/compile project)
  (let [src-nses (b/namespaces-on-classpath
                  :classpath (map io/file (distinct (:source-paths project)))
                  :ignore-unreadable? false)
        src-nses (set/difference (set src-nses)
                                 (set (map symbol cloverage-exclude-ns)))
        test-nses (b/namespaces-on-classpath
                   :classpath (map io/file (distinct (:test-paths project)))
                   :ignore-unreadable? false)
        cpath (cp/get-classpath project)
        mode (or mode "clojuretest")]
    (binding [utils/*sh-print-output* true]
      (utils/sh "java" "-cp" (str/join ":" cpath)
                "coa.droid_test.internal.TestRunner" "-mode" mode
                ":src" (map str src-nses)
                ":test" (map str test-nses)))))
