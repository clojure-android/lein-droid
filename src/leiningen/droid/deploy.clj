(ns leiningen.droid.deploy
  "Functions and subtasks that install and run the application on the
  device and manage its runtime."
  (:use [leiningen.core.main :only (debug info abort)]
        [leiningen.droid.manifest :only (get-launcher-activity
                                         get-package-name)]
        [leiningen.droid.utils :only (sh ensure-paths dev-build? append-suffix)]
        [reply.main :only (launch-nrepl)]))

(defn- device-list
  "Returns the list of currently attached devices."
  [adb-bin]
  (let [output (rest (sh adb-bin "devices"))] ;; Ignore the first line
    (remove nil?
            (map #(let [[_ serial type] (re-find #"([^\t]+)\t([^\t]+)" %)]
                    (when serial
                      {:serial serial, :type type}))
                 output))))

(defn- choose-device
  "If there is only one device attached returns its serial number,
  otherwise prompts user to choose the device to work with. If no
  devices are attached aborts the execution."
  [adb-bin]
  (let [devices (device-list adb-bin)]
    (case (count devices)
      0 (abort "No devices are attached..")
      1 (:serial (first devices))
      (do
        (dotimes [i (count devices)]
          (println (format "%d. %s\t\t%s" (+ i 1) (:serial (nth devices i)))
                   (:type (nth devices i))))
        (print (format "Enter the number 1..%d to choose the device: "
                       (count devices)))
        (flush)
        (let [answer (- (Integer/parseInt (read-line)) 1)]
          (:serial (nth devices answer)))))))

(defn get-device-args
  "Returns a list of adb arguments that specify the device adb should be
  working against. Calls `choose-device` if `adb-args` parameter is
  nil."
  [adb-bin device-args]
  (or device-args
      (list "-s" (choose-device adb-bin))))

(defn install
  "Installs the APK on the only (or specified) device or emulator."
  [{{:keys [adb-bin out-apk-path manifest-path]} :android :as project}
   & device-args]
  (info "Installing APK...")
  (ensure-paths adb-bin)
  (let [apk-path (if (dev-build? project)
                   (append-suffix out-apk-path "debug")
                   out-apk-path)
        device (get-device-args adb-bin device-args)]
    (ensure-paths apk-path)
    ;; Uninstall old APK first.
    (sh adb-bin device "uninstall" (get-package-name manifest-path))
    (sh adb-bin device "install" "-r" apk-path)))

(defn run
  "Launches the installed APK on the connected device."
  [{{:keys [adb-bin manifest-path]} :android} & device-args]
  (info "Launching APK...")
  (ensure-paths adb-bin manifest-path)
  (let [device (get-device-args adb-bin device-args)]
    (sh adb-bin device "shell" "am" "start" "-n"
        (get-launcher-activity manifest-path))))

(defn forward-port
  "Binds a port on the local machine to the port on the device
  allowing to connect to the remote REPL."
  [{{:keys [adb-bin repl-device-port repl-local-port]} :android} & device-args]
  (info "Binding device port" repl-device-port
        "to local port" repl-local-port "...")
  (ensure-paths adb-bin)
  (let [device (get-device-args adb-bin device-args)]
    (sh adb-bin device "forward"
        (str "tcp:" repl-local-port)
        (str "tcp:" repl-device-port))))

(defn repl
  "Connects to a remote nREPL server on the device using REPLy."
  [{{:keys [repl-local-port]} :android}]
  (launch-nrepl {:attach (str "localhost:" repl-local-port)}))

(defn deploy
  "Installs the APK to the device, executes it and forwards a port from
  the device to the local machine."
  [{{:keys [adb-bin]} :android :as project} & device-args]
  (let [device (get-device-args adb-bin device-args)]
    (apply install project device)
    (apply run project device)
    (apply forward-port project device)))