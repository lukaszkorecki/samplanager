(ns samplanager.core
  "CLI entry point for samplanager - finds duplicate audio samples."
  (:require [babashka.fs :as fs]
            [samplanager.scanner :as scanner]
            [samplanager.report :as report])
  (:gen-class))

(defn find-duplicates
  "Scans root-dir for audio files, finds duplicates by checksum.
   Returns the report map."
  [root-dir]
  (let [audio-files (scanner/scan-audio-files root-dir)
        candidates (report/group-by-size audio-files)
        checksum-groups (report/group-by-checksum candidates)
        duplicates (report/find-duplicates checksum-groups)]
    (report/build-report audio-files duplicates)))

(defn -main
  "Scans the given root directory for duplicate audio files and
   outputs a JSON report to stdout."
  [& args]
  (let [root-dir (first args)]
    (when (or (nil? root-dir) (not (fs/directory? root-dir)))
      (binding [*out* *err*]
        (println "Usage: samplanager <root-directory>")
        (println "Error: Please provide a valid directory path."))
      (System/exit 1))
    (binding [*out* *err*]
      (println "Scanning" root-dir "..."))
    (let [result (find-duplicates root-dir)]
      (binding [*out* *err*]
        (println "Found" (:found-files-count result) "audio files,"
                 (:duplicate-files-count result) "involved in duplicates."))
      (println (report/report->json result)))))
