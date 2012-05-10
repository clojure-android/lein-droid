(ns leiningen.droid.run
  "Functions and subtasks that run the application on the device and
  manage its runtime. "
  (:use [leiningen.core.main :only (debug info) :rename {debug print-debug}]
        [leiningen.droid.manifest :only (get-launcher-activity)]
        [leiningen.droid.utils :only (sh)]
        [reply.main :only (launch-nrepl)]))

(defn run
  "Launches the installed APK on the connected device."
  [{{:keys [sdk-path manifest-path]} :android}]
  (info "Launching APK...")
  (let [adb-bin (str sdk-path "/platform-tools/adb")]
    (.waitFor (sh adb-bin "shell am start"
                  "-n" (get-launcher-activity manifest-path)))))

(defn forward-port
  "Binds a port on the local machine to the port on the device
  allowing to connect to the remote REPL."
  [{{:keys [sdk-path repl-device-port repl-local-port]} :android}]
  (info "Binding device port" repl-device-port
        "to local port" repl-local-port "...")
  (let [adb-bin (str sdk-path "/platform-tools/adb")]
    (.waitFor (sh adb-bin "forward" (str "tcp:" repl-local-port) (str "tcp:" repl-device-port)))))

(defn repl
  "Connects to a remote nREPL server on the device using REPLy."
  [{{:keys [repl-local-port]} :android}]
  (launch-nrepl {:attach (str "localhost:" repl-local-port)}))