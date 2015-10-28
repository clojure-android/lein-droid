(ns leiningen.droid.inc-build
  "Subtasks related to build process as a whole. Decides what to run and
  what not to. It stores the subtasks and files dependencies as function."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clj-http.client :as http]
            [clojure.xml :as xml]
            [leiningen.core.main :refer [info debug]])
  (:use [leiningen.droid.utils :only [platform append-suffix absolutize]]))

(defn- download-zip
  "Connect to the internet and download given application/zip file. Once the file is downloaded
  return the file (io/file) from the local."
  [path url]
  (let [head (http/head url)
        headers (:headers head)
        type-of-contents (:Content-type headers)]
    (debug "Content type is" type-of-contents)
    (when (= type-of-contents "application/zip")
      (let [relative-file-name (subs url (inc (.lastIndexOf url "/")))
            dir (absolutize path "temp")
            _ (.mkdir (java.io.File. dir))
            file-name (absolutize dir relative-file-name)]
        (info "Downloading" file-name "from" url)
        (io/copy (:body (http/get url {:as :stream}))
                 (java.io.File. file-name))
        file-name))))

(defn- download-xml
  "Connect to the internet and download a given xml file. Return the clojure map that's parsed
  from the given xml."
  [url]
  (let [head (http/head url)
        headers (:headers head)
        type-of-contents (:Content-type headers)]
    (debug "Content type is" type-of-contents)
    (info "Downloading repository at" url)
    (when (= type-of-contents "application/xml")
      (-> (http/get url)
          :body
          .getBytes
          java.io.ByteArrayInputStream.
          xml/parse))))

(defn- repository
  "Links to the repository metadata for the components."
  []
  {:sdk:platform "https://dl.google.com/android/repository/repository-10.xml"
   :sdk:platform-tool "https://dl.google.com/android/repository/repository-10.xml"
   :sdk:build-tool "https://dl.google.com/android/repository/repository-10.xml"
   :sdk:tool "https://dl.google.com/android/repository/repository-10.xml"
   :sdk:sample "https://dl.google.com/android/repository/repository-10.xml"
   :sdk:source "https://dl.google.com/android/repository/repository-10.xml"
   :google-apis "https://dl.google.com/android/repository/addon.xml"
   :android-support-lib "https://dl.google.com/android/repository/addon-6.xml"
   :glass-dev-kit "https://dl-ssl.google.com/glass/gdk/addon.xml"
   :intel-emulator "https://dl.google.com/android/repository/extras/intel/addon.xml"
   :systel-images "https://dl.google.com/android/repository/sys-img/android/sys-img.xml"
   :android-wear-sysimages "https://dl.google.com/android/repository/sys-img/android-wear/sys-img.xml"
   :android-tv-sysimages "https://dl.google.com/android/repository/sys-img/android-tv/sys-img.xml"
   :google-apis-sysimages "https://dl.google.com/android/repository/sys-img/google_apis/sys-img.xml"})

(defn- absolutize-url
  "If the given url is not absolute then, return the absolute url by appending url to the base-url. If
  url is absolute (i.e. it starts with http:// or https://) then return the url without any modification.
  Move this function to utils."
  [base-url url]
  (if (or (.startsWith url "http://") (.startsWith url "https://"))
    url
    (str (subs base-url 0 (inc (.lastIndexOf base-url "/"))) url)))

(defn- filter-tags
  "Filter the nodes/children with given tags from the given sequence."
  [tag child-vector]
  (filter #(= (:tag %) tag) child-vector))

(defn- fetch-first-tag
  "From the given vector extract first children matching given keyword tag."
  [tag child-vector]
  (first (filter-tags tag child-vector)))

(defn- fetch-content-for-nested-keyword
  "Walk down the hierarchy of one child, in order to collect given tag."
  [tag-keyword child]
  #_(info "fetch-content-for-nested-keyword: Keyword is" tag-keyword "child node is:" child)
  (let [content (:content child)
        sdk-api-level-child (filter-tags tag-keyword content)
        api-level (first (:content (first sdk-api-level-child)))]
    (read-string api-level)))

(defn- extract-platform-dependent-zip
  [archives platform]
  "It goes deep and retrieves file names from archives vector. We need to modify this to use :sdk:host-os tag
  instead of .contains comparison."
  (debug "extract-platform-dependent-zip: Archives are" (type platform) archives)
  (let [files-names (map (partial fetch-content-for-nested-keyword :sdk:url) archives)
        file (filter #(.contains (str %) platform) files-names)]
    (debug "extract-platform-dependent-zip: Files are" files-names)
    file))

(defn- extract-platform
  "From the xml, extract the sdk:platform node matching the given target-api."
  [xml target-version]
  (let [content-body (:content xml)
        sdk-platforms (filter-tags :sdk:platform content-body)
        api-levels (map (partial fetch-content-for-nested-keyword :sdk:api-level) sdk-platforms)
        max-api (if target-version (read-string target-version) (apply max api-levels))
        max-api-index (.indexOf api-levels max-api)]
    (debug "extract-platform: Target API" max-api)
    (debug "extract-platform: Available API levels are" api-levels)
    (:content (get (vec sdk-platforms) max-api-index))))

(defn- extract-platform-url
  "Given the xml map of the repository determine the download url for the :sdk-platform. This function returns the
  URL for the sdk that matche target-version with the :sdk:api-level from all teh platforms. It will give the
  absolute URL of the resource. For now it only works with :sdk:platform tags in xml. It should be generic
  enough to extract any leaf nodes."
  [xml base-url target-version]
  (let [sdk-platform-content (extract-platform xml target-version)
        archives-content (:content (fetch-first-tag :sdk:archives sdk-platform-content))
        platform (platform)
        file-name (if (< 1 (count archives-content))
                    (extract-platform-dependent-zip archives-content platform)
                    (:content (fetch-first-tag :sdk:url (:content (first archives-content)))))]
    (debug "extract-platform-url: SDK platform content is" sdk-platform-content)
    (debug "extract-platform-url: Archives content is" archives-content)
    (info "extract-platform-url: File to download" (first file-name))
    (absolutize-url base-url (str (first file-name)))))

(defn- filter-ith-max
  "From the given nested (only two levels), matrix of numbers filter only the rows that are max at i-th column."
  [i matrix]
  (let [ith-max (apply max (map #(get % i) matrix))]
    (filter #(= ith-max (get % i)) matrix)))

(defn- extract-platform-tool-url
  "Given the xml map of the repository determine the download url for the :sdk-platform-tool. This will return the
  latest url. It will give the absolute URL of the resource."
  [xml base-url]
  (let [content-body (:content xml)
        sdk-platform-tools (filter-tags :sdk:platform-tool content-body)
        sdk-platform-tools-content (map #(:content %) sdk-platform-tools)
        sdk-revisions (map (partial fetch-first-tag :sdk:revision) sdk-platform-tools-content)
        major-api-levels (map (partial fetch-content-for-nested-keyword :sdk:major) sdk-revisions)
        minor-api-levels (map (partial fetch-content-for-nested-keyword :sdk:minor) sdk-revisions)
        micro-api-levels (map (partial fetch-content-for-nested-keyword :sdk:micro) sdk-revisions)
        revisions (map vector major-api-levels minor-api-levels micro-api-levels)
        max-revision (first (filter-ith-max 2 (filter-ith-max 1 (filter-ith-max 0 revisions))))
        required-platform-tool-index (first (keep-indexed #(if (and
                                                                (and
                                                                 (= (get max-revision 0) (get %2 0))
                                                                 (= (get max-revision 1) (get %2 1)))
                                                                (= (get max-revision 2) (get %2 2)))
                                                             %1) revisions))
        required-platform-tool-content (:content (nth sdk-platform-tools required-platform-tool-index))
        archives-content (:content (fetch-first-tag :sdk:archives required-platform-tool-content))
        platform (platform)
        file-name (if (< 1 (count archives-content))
                    (extract-platform-dependent-zip archives-content platform)
                    (:content (fetch-first-tag :sdk:url (:content (first archives-content)))))]
    (debug "extract-platform-tool-url: SDK revisions" sdk-revisions)
    (debug "extract-platform-tool-url: Major API levels are" major-api-levels)
    (debug "extract-platform-tool-url: Minor API levels are" minor-api-levels)
    (debug "extract-platform-tool-url: Micro API levels are" micro-api-levels)
    (debug "extract-platform-tool-url: Revisions" revisions)
    (debug "extract-platform-tool-url: Max revisions" max-revision)
    (debug "extract-platform-tool-url: Platform tools" sdk-platform-tools)
    (debug "extract-platform-tool-url: Max required platform tool" required-platform-tool-content)
    (debug "extract-platform-tool-url: Required platform tool index" required-platform-tool-index)
    (debug "extract-platform-tool-url: Archives content" archives-content)
    (info "extract-platform-tool-url: File to download" (first file-name))
    (absolutize-url base-url (str (first file-name)))))

(defn- extract-build-tool-url
  "Given the xml map of the repository determine the download url for the :sdk:build-tool. This will return the
  url of the zip matching the major revision. If more than one major revisions then download the latest with
  mathing major. It will give the absolute URL of the resource."
  [xml base-url target-version]
  (let [content-body (:content xml)
        sdk-build-tools (filter-tags :sdk:build-tool content-body)
        sdk-build-tools-content (map #(:content %) sdk-build-tools)
        sdk-revisions (map (partial fetch-first-tag :sdk:revision) sdk-build-tools-content)
        major-api-levels (map (partial fetch-content-for-nested-keyword :sdk:major) sdk-revisions)
        minor-api-levels (map (partial fetch-content-for-nested-keyword :sdk:minor) sdk-revisions)
        micro-api-levels (map (partial fetch-content-for-nested-keyword :sdk:micro) sdk-revisions)
        revisions (map vector major-api-levels minor-api-levels micro-api-levels)
        max-revision (first (filter-ith-max 2 (filter-ith-max 1 (filter
                                                                 #(= (get % 0) (read-string target-version))
                                                                 revisions))))
        required-build-tool-index (first (keep-indexed #(if (and
                                                             (and
                                                              (= (get max-revision 0) (get %2 0))
                                                              (= (get max-revision 1) (get %2 1)))
                                                             (= (get max-revision 2) (get %2 2)))
                                                          %1) revisions))
        required-build-tool-content (:content (nth sdk-build-tools required-build-tool-index))
        archives-content (:content (fetch-first-tag :sdk:archives required-build-tool-content))
        platform (platform)
        file-name (if (< 1 (count archives-content))
                    (extract-platform-dependent-zip archives-content platform)
                    (:content (fetch-first-tag :sdk:url (:content (first archives-content)))))]
    (debug "extract-build-tool-url: SDK revisions" sdk-revisions)
    (debug "extract-build-tool-url: Major API levels are" major-api-levels)
    (debug "extract-build-tool-url: Minor API levels are" minor-api-levels)
    (debug "extract-build-tool-url: Micro API levels are" micro-api-levels)
    (debug "extract-build-tool-url: Revisions" revisions)
    (debug "extract-build-tool-url: Max revisions" max-revision)
    (debug "extract-build-tool-url: build tools" sdk-build-tools)
    (debug "extract-build-tool-url: Max required build tool" required-build-tool-content)
    (debug "extract-build-tool-url: Required build tool index" required-build-tool-index)
    (debug "extract-build-tool-url: Archives content" archives-content)
    (info "extract-build-tool-url: File to download" (first file-name))
    (absolutize-url base-url (str (first file-name)))))

(defn- extract-tool-url
  "Given the xml map of the repository determine the download url for the :sdk:tool. This will return the
  latest url. It will give the absolute URL of the resource."
  [xml base-url]
  (let [content-body (:content xml)
        sdk-tools (filter-tags :sdk:tool content-body)
        sdk-tools-content (map #(:content %) sdk-tools)
        sdk-revisions (map (partial fetch-first-tag :sdk:revision) sdk-tools-content)
        major-api-levels (map (partial fetch-content-for-nested-keyword :sdk:major) sdk-revisions)
        minor-api-levels (map (partial fetch-content-for-nested-keyword :sdk:minor) sdk-revisions)
        micro-api-levels (map (partial fetch-content-for-nested-keyword :sdk:micro) sdk-revisions)
        revisions (map vector major-api-levels minor-api-levels micro-api-levels)
        max-revision (first (filter-ith-max 2 (filter-ith-max 1 (filter-ith-max 0 revisions))))
        required-tool-index (first (keep-indexed #(if (and
                                                       (and
                                                        (= (get max-revision 0) (get %2 0))
                                                        (= (get max-revision 1) (get %2 1)))
                                                       (= (get max-revision 2) (get %2 2)))
                                                    %1) revisions))
        required-tool-content (:content (nth sdk-tools required-tool-index))
        archives-content (:content (fetch-first-tag :sdk:archives required-tool-content))
        platform (platform)
        file-name (if (< 1 (count archives-content))
                    (extract-platform-dependent-zip archives-content platform)
                    (:content (fetch-first-tag :sdk:url (:content (first archives-content)))))]
    (debug "extract-tool-url: SDK revisions" sdk-revisions)
    (debug "extract-tool-url: Major API levels are" major-api-levels)
    (debug "extract-tool-url: Minor API levels are" minor-api-levels)
    (debug "extract-tool-url: Micro API levels are" micro-api-levels)
    (debug "extract-tool-url: Revisions" revisions)
    (debug "extract-tool-url: Max revisions" max-revision)
    (debug "extract-tool-url: Tools" sdk-tools)
    (debug "extract-tool-url: Max required tool" required-tool-content)
    (debug "extract-tool-url: Required tool index" required-tool-index)
    (debug "extract-tool-url: Archives content" archives-content)
    (info "extract-build-tool-url: File to download" (first file-name))
    (absolutize-url base-url (str (first file-name)))))

(defn- get-download-url
  "Traverse the given xml map for the latest download url of the given sdk. In case of build-tools and platforms,
  this function returns the sdk matching the given target-version (:sdk:api-level in case of platforms and
  sdk:major in case of build-tools)."
  [xml sdk base-url & target-version]
  (case sdk
    :sdk:platform (extract-platform-url xml base-url (first target-version))
    :sdk:platform-tool (extract-platform-tool-url xml base-url)
    :sdk:build-tool (extract-build-tool-url xml base-url (first target-version))
    :sdk:tool (extract-tool-url xml base-url)
    "sdk not found."))

(defn- unzip
  "Unzip the given file. By default this function simply unzips the file for now, in the same parent directory."
  [fileName]
  (with-open [fis (java.io.FileInputStream. fileName)
              zis (java.util.zip.ZipInputStream. (java.io.BufferedInputStream. fis))]
    (loop [entry (.getNextEntry zis)]
      (if entry
        (do
          (if (not (.isDirectory entry))
            (do
              (io/make-parents (str op-folder (.getName entry)))
              (with-open [fos (java.io.FileOutputStream. (str op-folder (.getName entry)))
                          dest (java.io.BufferedOutputStream. fos 2048)]
                (loop [data (byte-array 2048)
                       c (.read zis data 0 2048)]
                  (if (not= c -1)
                    (do
                      (.write dest data 0 c)
                      (recur (byte-array 2048) (.read zis data 0 2048))))))))
          (recur (.getNextEntry zis)))))))

(defn ensure-sdk
  "Ensure if required libraries in the sdk are present or not. If not present
  then this function will download the sdk automatically from the Android official
  site and unzip the sdk in temp directory in :sdk-path."
  [{{:keys [sdk-path target-version]} :android :as project}]
  (let [url "https://dl.google.com/android/repository/repository-10.xml"
        xml (download-xml url)
        build-tool-major-version (first
                                  (:content (first
                                             (filter-tags
                                              :sdk:major
                                              (:content
                                               (first
                                                (filter-tags
                                                 :sdk:min-tools-rev
                                                 (extract-platform xml target-version))))))))
        platform-download-url (get-download-url xml :sdk:platform url target-version)
        platform-tool-download-url (get-download-url xml :sdk:platform-tool url)
        build-tool-download-url (get-download-url xml :sdk:build-tool url build-tool-major-version)
        tool-download-url (get-download-url xml :sdk:tool url)]
    (info "Download url for platform is" platform-download-url)
    (download-zip sdk-path platform-download-url)
    (info "Download url for platform-tool" platform-tool-download-url)
    (download-zip sdk-path platform-tool-download-url)
    (info "Download url for build-tool" build-tool-download-url)
    (download-zip sdk-path build-tool-download-url)
    (info "Download url for tool" tool-download-url)
    (download-zip sdk-path tool-download-url)))

(defn get-subtask-dependencies
  "Get the file dependencies for subtasks. This is a static class returning a map
  of subtask names and corresponding file/directory dependency in a vector. The
  key name has to be the exact subtask name. The dependency paths are relative
  (to the project)."
  [project]
  {"generate-manifest" ["project.clj"]
   "generate-resource-code" ["res", "target/debug/AndroidManifest.xml"]
   "generate-build-constants" ["project.clj"]
   "compile" (flatten (distinct (project :source-paths)))
   "create-dex" ["target/debug/classes"]
   "crunch-resources" ["res"]
   "package-resources" (flatten ["res" "target/debug/AndroidManifest.xml"
                        (get-in project [:android :out-res-path])
                        (get-in project [:android :assets-paths])
                        (get-in project [:android :assets-gen-path])])
   "create-apk" (flatten ["target/debug/classes.dex"
                 (get-in project [:android :out-res-pkg-path])])
   "sign-apk" [(append-suffix (get-in project [:android :out-apk-path]) "unaligned")]
   "zipalign-apk" [(append-suffix (get-in project [:android :out-apk-path]) "unaligned")]})

(defn- walk
  "Walk the given directory searching for files. For now we search all the files
  irrespective of the type (extension i.e. java or clj or xml)."
  [dirpath]
  (doall (filter #(not (.isDirectory %)) (file-seq dirpath))))

(defn- latest-timestamp-in-directory
  "Find the timestamp of last modified file in a directory.
  Recursively walk the directories and find timestamp of file
  that was recently modified. If the directory is empty
  then return the timestamp of the outermost directory as the placeholder."
  [dir]
  (let [timestamps (map #(.lastModified %) (walk dir))]
    (debug "Timestamps in" dir "are" timestamps)
    (if (empty timestamps)
      (.lastModified dir)
      (apply max timestamps))))

(defn- file-modified?
  "Check if input file is modified, since the last time
  recorded. Return false iff recorded timestamp is same as the one
  that's read recently."
  [timestamp-file subtask input-file]
  (if (not (.exists timestamp-file))
    true
    (let [input-file-time (if (.isDirectory input-file)
                            (latest-timestamp-in-directory input-file)
                            (.lastModified input-file))
          input-file-name (str input-file)
          timestamps (edn/read-string (slurp timestamp-file))
          recorded-time (get-in timestamps [subtask input-file-name])]
      (if (and recorded-time (== recorded-time input-file-time))
        false
        true))))

(defn- partial-file-modified?
  "Partial file-modified? function which takes the timestamp-file."
  [timestamp-file subtask]
  (partial file-modified? timestamp-file subtask))

(defn input-modified?
  "Check if some of the given input files/files in dirs are modified. Return true if
  at least one file has been modified."
  [timestamp-file-name subtask input-path-names]
  (let [input-paths (map io/file input-path-names)
        timestamp-file (io/file timestamp-file-name)]
    (some (partial-file-modified? timestamp-file subtask) input-paths)))

(defn- write-timestamp
  "First read the current timestamp (for files for subtasks) if exists and update
  the timestamps in."
  [timestamp-file-name subtask path]
  (let [timestamp-file (io/file timestamp-file-name)
        path-timestamp (if (.isDirectory path)
                         (latest-timestamp-in-directory path)
                         (.lastModified path))]
    (debug "Path is" path "timestamp is" path-timestamp)
    (if (not (.exists timestamp-file))
      (spit timestamp-file-name (prn-str {(str subtask) {(str path) path-timestamp}}))
      (let [timestamps (edn/read-string (slurp timestamp-file))
            updated-timestamps (update-in timestamps [subtask] assoc (str path) path-timestamp)]
        (spit timestamp-file-name (prn-str updated-timestamps))))))

(defn record-timestamps
  "After execution of each subtask, record the timestamps of the files/directories,
  that found modified. If it's a directory then record the timestamp of the recently
  modified file. It recursively traverses directory structure to get the recent timestamp."
  [timestamp-file-name subtask path-names]
  (let [paths (map io/file path-names)]
    (debug "Recording timestamps for" subtask paths)
    (doall (map (partial write-timestamp timestamp-file-name subtask) paths))))
