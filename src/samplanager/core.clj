(ns samplanager.core
  "CLI entry point for samplanager - finds duplicate audio samples."
  (:require [babashka.fs :as fs]
            [mokujin.log :as log]
            [samplanager.logging :as logging]
            [samplanager.scanner :as scanner]
            [samplanager.report :as report]
            [samplanager.tui :as tui])
  (:gen-class))

(defn find-duplicates
  "Scans root-dir for audio files, finds duplicates by checksum.
   Returns {:report counts-map, :duplicates [[path ...] ...]}."
  ([root-dir]
   (find-duplicates root-dir nil))
  ([root-dir {:keys [scan-progress-fn scan-complete-fn
                     checksum-progress-fn]}]
   (log/info "starting duplicate scan" {:root-dir root-dir})
   (let [audio-files (scanner/scan-audio-files
                       root-dir
                       scan-progress-fn)
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
     (log/info "scan complete" {:found (:found-files-count report-map)
                                :duplicates (:duplicate-files-count report-map)
                                :groups (:duplicate-groups-count report-map)})
     {:report report-map
      :duplicates duplicates})))

(defn -main
  [& args]
  (logging/setup!)
  (let [root-dir (first args)
        output-file (second args)]
    (when (or (nil? root-dir) (nil? output-file) (not (fs/directory? root-dir)))
      (binding [*out* *err*]
        (println "Usage: samplanager <root-directory> <output-file>")
        (println "  Scans for duplicate audio files and writes the list to output-file."))
      (System/exit 1))
    (log/info "samplanager starting" {:root-dir root-dir :output-file output-file})
    (tui/run-tui {:root-dir root-dir :output-file output-file})
    (future
      (try
        (let [{:keys [report duplicates]}
              (find-duplicates root-dir
                               {:scan-progress-fn tui/update-scan-progress!
                                :scan-complete-fn tui/scan-complete!
                                :checksum-progress-fn tui/update-checksum-progress!})]
          (log/info "writing output" {:output-file output-file})
          (spit output-file (report/->json {:duplicates duplicates}))
          (log/info "output written")
          (tui/done! report))
        (catch Exception e
          (log/error e "fatal error during scan")
          (tui/error! (.getMessage e)))))
    (tui/await-exit)
    (log/info "TUI exited, shutting down")
    (shutdown-agents)))
