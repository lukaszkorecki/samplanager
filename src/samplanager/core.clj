(ns samplanager.core
  "CLI entry point for samplanager - finds duplicate audio samples."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [samplanager.log :as log]
            [samplanager.scanner :as scanner]
            [samplanager.report :as report]
            [samplanager.tui :as tui])
  (:gen-class))

(def cli-options
  [["-o" "--output FILE" "Output JSON file (required)"]
   ["-d" "--debug" "Write debug log to debug.log in CWD"]
   ["-h" "--help" "Show this help"]])

(defn- usage
  [summary]
  (str/join \newline
            ["samplanager — find duplicate audio samples"
             ""
             "Usage: samplanager [options] <dir> [dir ...]"
             ""
             "Options:"
             summary
             ""
             "Scans one or more directories for duplicate audio files"
             "and writes the duplicate list to the output file as JSON."]))

(defn- validate-args
  "Parses and validates CLI args. Returns either
   {:dirs [...] :output-file \"...\"} or {:exit-message \"...\" :ok? bool}."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (str/join \newline errors) :ok? false}

      (nil? (:output options))
      {:exit-message "Error: --output is required" :ok? false}

      (empty? arguments)
      {:exit-message "Error: at least one directory is required" :ok? false}

      :else
      (let [bad-dirs (remove #(fs/directory? %) arguments)]
        (if (seq bad-dirs)
          {:exit-message (str "Error: not a directory: " (str/join ", " bad-dirs))
           :ok? false}
          {:dirs (vec arguments)
           :output-file (:output options)
           :debug? (boolean (:debug options))})))))

(defn find-duplicates
  "Scans dirs for audio files, finds duplicates by checksum.
   dirs is a vector of directory paths.
   Returns {:report counts-map, :duplicates [[path ...] ...]}."
  ([dirs]
   (find-duplicates dirs nil))
  ([dirs {:keys [scan-progress-fn scan-complete-fn
                 checksum-progress-fn]}]
   (log/debug "starting duplicate scan" {:dirs dirs})
   (let [audio-files (into []
                           (mapcat #(scanner/scan-audio-files % scan-progress-fn))
                           dirs)
         candidates (report/group-by-size audio-files)
         _ (when scan-complete-fn
             (scan-complete-fn {:count (count audio-files)
                                :candidates (count candidates)}))
         checksum-count (atom 0)
         checksum-groups (report/group-by-checksum
                          candidates
                          (when checksum-progress-fn
                            (fn [_path]
                              (let [n (swap! checksum-count inc)]
                                (when (zero? (mod n 10))
                                  (checksum-progress-fn n))))))
         duplicates (report/find-duplicates checksum-groups)
         report-map (report/build-report audio-files duplicates)]
     ;; send final checksum count
     (when checksum-progress-fn
       (checksum-progress-fn @checksum-count))
     (log/debug "scan complete" {:found (:found-files-count report-map)
                                 :duplicates (:duplicate-files-count report-map)
                                 :groups (:duplicate-groups-count report-map)})
     {:report report-map
      :duplicates duplicates})))

(defn -main
  [& args]
  (let [{:keys [dirs output-file debug? exit-message ok?]} (validate-args args)]
    (when exit-message
      (binding [*out* (if ok? *out* *err*)]
        (println exit-message))
      (System/exit (if ok? 0 1)))
    (when debug? (log/enable!))
    (log/debug "samplanager starting" {:dirs dirs :output-file output-file})
    (tui/run-tui {:dirs dirs :output-file output-file})
    (future
      (try
        (let [{:keys [report duplicates]}
              (find-duplicates dirs
                               {:scan-progress-fn tui/update-scan-progress!
                                :scan-complete-fn tui/scan-complete!
                                :checksum-progress-fn tui/update-checksum-progress!})]
          (log/debug "writing output" {:output-file output-file})
          (spit output-file (report/->json {:duplicates duplicates}))
          (log/debug "output written")
          (tui/done! report))
        (catch Exception e
          (log/error e "fatal error during scan" nil)
          (tui/error! (.getMessage e)))))
    (tui/await-exit)
    (tui/print-summary)
    (log/debug "TUI exited, shutting down")
    (shutdown-agents)))
