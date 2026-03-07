(ns samplanager.core
  "CLI entry point for samplanager - finds duplicate audio samples."
  (:require [babashka.fs :as fs]
            [samplanager.scanner :as scanner]
            [samplanager.report :as report])
  (:gen-class))

(defn find-duplicates
  "Scans root-dir for audio files, finds duplicates by checksum.
   Returns {:report counts-map, :duplicates [[path ...] ...]}."
  [root-dir]
  (let [audio-files (scanner/scan-audio-files root-dir)
        candidates (report/group-by-size audio-files)
        checksum-groups (report/group-by-checksum candidates)
        duplicates (report/find-duplicates checksum-groups)]
    {:report (report/build-report audio-files duplicates)
     :duplicates duplicates}))

(defn -main
  "Scans the given root directory for duplicate audio files.
   Outputs a JSON summary to stdout and writes duplicate file list
   to the specified output file."
  [& args]
  (let [root-dir (first args)
        output-file (second args)]
    (when (or (nil? root-dir) (nil? output-file) (not (fs/directory? root-dir)))
      (binding [*out* *err*]
        (println "Usage: samplanager <root-directory> <output-file>")
        (println "  Scans for duplicate audio files and writes the list to output-file.")
        (println "  A JSON summary is printed to stdout."))
      (System/exit 1))
    (binding [*out* *err*]
      (println "Scanning" root-dir "..."))
    (let [{:keys [report duplicates]} (find-duplicates root-dir)]
      (spit output-file (report/->json {:duplicates duplicates}))
      (binding [*out* *err*]
        (println "Found" (:found-files-count report) "audio files,"
                 (:duplicate-files-count report) "involved in duplicates.")
        (println "Duplicate file list written to" output-file))
      (println (report/->json report))
      (shutdown-agents))))
