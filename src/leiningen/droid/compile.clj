(ns leiningen.droid.compile
  "This part of the plugin is responsible for the project compilation."
  (:refer-clojure :exclude [compile])
  (:require [bultitude.core :as bultitude]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.compile :refer [stale-namespaces]]
            [leiningen.core.classpath :refer [get-classpath]]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :refer [debug info abort]]
            [leiningen.droid.manifest :refer [get-package-name]]
            [leiningen.droid.utils :refer [ensure-paths dev-build?]]
            leiningen.javac)
  (:import java.util.regex.Pattern))

;; ### Pre-compilation tasks

(defn eval-in-project
  ([project form init]
   (eval/prep project)
   (eval/eval-in project
                 `(do ~@(map (fn [[k v]] `(set! ~k ~v)) (:global-vars project))
                      ~init
                      ~@(:injections project)
                      ~form)))
  ([project form] (eval-in-project project form nil)))

(defn save-data-readers-to-resource
  "Save project's *data-readers* value to application's resources so
  it can be later retrieved in runtime. This is necessary to be able
  to use data readers when developing in REPL on the device."
  [{{:keys [assets-gen-path]} :android :as project}]
  (.mkdirs (io/file assets-gen-path))
  (eval-in-project
   project
   `(do (require 'clojure.java.io)
        (spit (clojure.java.io/file ~assets-gen-path "data_readers.clj")
              (into {} (map (fn [[k# v#]]
                              [k# (symbol (subs (str v#) 2))])
                            clojure.core/*data-readers*))))))

;; ### Compilation

(defn namespaces-to-compile
  "Takes project and returns a set of namespaces that should be AOT-compiled."
  [{{:keys [aot aot-exclude-ns]} :android :as project}]
  (let [all-nses (bultitude/namespaces-on-classpath
                  :classpath (map io/file (get-classpath project))
                  :ignore-unreadable? false)
        include (case aot
                  :all (stale-namespaces (assoc project :aot :all))
                  :all-with-unused all-nses
                  aot)
        exclude aot-exclude-ns

        {include-nses false, include-regexps true}
        (group-by #(instance? Pattern %) include)

        {exclude-nses false, exclude-regexps true}
        (group-by #(instance? Pattern %) exclude)]
    (->> (set/difference (set (map str (if (seq include-regexps)
                                         all-nses include-nses)))
                         (set exclude-nses))
         (filter (fn [ns] (if (seq include-regexps)
                           (some #(re-matches % ns) include-regexps)
                           true)))
         (remove (fn [ns] (if (seq exclude-regexps)
                           (some #(re-matches % ns) exclude-regexps)
                           false)))
         (concat (if (seq include-regexps)
                   include-nses ()))
         (map symbol))))

(defn compile-clojure
  "Compiles Clojure files into .class files.

  If `:aot` project parameter equals `:all` then compiles the
  necessary dependencies. If `:aot` equals `:all-with-unused` then
  compiles all namespaces of the dependencies whether they were
  referenced in the code or not. The latter is useful for the
  REPL-driven development.

  Uses neko to set compilation flags. Some neko macros and
  subsequently project code depends on them to eliminate
  debug-specific code when building the release."
  [{{:keys [enable-dynamic-compilation start-nrepl-server
            manifest-path repl-device-port ignore-log-priority
            lean-compile skummet-skip-vars]}
    :android
    {:keys [nrepl-middleware]} :repl-options
    :as project}]
  (info "Compiling Clojure files...")
  (debug "Project classpath:" (get-classpath project))
  (let [nses (namespaces-to-compile project)
        dev-build (dev-build? project)
        package-name (try (get-package-name manifest-path)
                          (catch Exception ex nil))
        opts (cond-> {:neko.init/release-build (not dev-build)
                      :neko.init/start-nrepl-server start-nrepl-server
                      :neko.init/nrepl-port repl-device-port
                      :neko.init/enable-dynamic-compilation
                      enable-dynamic-compilation
                      :neko.init/ignore-log-priority ignore-log-priority
                      :neko.init/nrepl-middleware (list 'quote nrepl-middleware)
                      :neko.init/package-name package-name}
               (not dev-build) (assoc :elide-meta
                                      [:doc :file :line :column :added :author
                                       :static :arglists :forms]))]
    (info (format "Build type: %s, dynamic compilation: %s, remote REPL: %s."
                  (if dev-build "debug" (if lean-compile "lean" "release"))
                  (if (or dev-build start-nrepl-server
                          enable-dynamic-compilation)
                    "enabled" "disabled")
                  (if (or dev-build start-nrepl-server) "enabled" "disabled")))
    (let [form
          (if lean-compile
            `(let [lean-var?# (fn [var#] (not (#{~@skummet-skip-vars}
                                              (str var#))))]
               (binding [~'clojure.core/*lean-var?* lean-var?#
                         ~'clojure.core/*lean-compile* true
                         ~'clojure.core/*compiler-options* ~opts]
                 (doseq [namespace# '~nses]
                   (println "Compiling" namespace#)
                   (clojure.core/compile namespace#))
                 (shutdown-agents)))
            `(binding [*compiler-options* ~opts]
               ;; If expectations is present, don't run it during compilation.
               (doseq [namespace# '~nses]
                 (println "Compiling" namespace#)
                 (clojure.core/compile namespace#))
               (try (require 'expectations)
                    ((resolve 'expectations/disable-run-on-shutdown))
                    (catch Throwable _# nil))
               (shutdown-agents)))]
      (.mkdirs (io/file (:compile-path project)))
      (try (eval-in-project project form)
           (info "Compilation succeeded.")
           (catch Exception e
             (abort "Compilation failed."))))))

(defn compile
  "Compiles both Java and Clojure source files."
  [{{:keys [sdk-path gen-path lean-compile]} :android,
    java-only :java-only :as project}]
  (ensure-paths sdk-path)
  (let [project (-> project
                    (update-in [:prep-tasks] (partial remove #{"compile"})))]
    (leiningen.javac/javac project)
    (when-not java-only
      (save-data-readers-to-resource project)
      (compile-clojure project))))
