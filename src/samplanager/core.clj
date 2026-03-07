(ns samplanager.core
  "CLI entry point for samplanager - finds duplicate audio samples."
  (:require [babashka.fs :as fs]
            [samplanager.scanner :as scanner]
            [samplanager.report :as report]
            [samplanager.tui :as tui])
  (:gen-class))

(defn find-duplicates
  "Scans root-dir for audio files, finds duplicates by checksum.
   Returns {:report counts-map, :duplicates [[path ...] ...]}."
  ([root-dir]
   (find-duplicates root-dir nil))
  ([root-dir progress-fn]
   (let [audio-files (scanner/scan-audio-files root-dir)
         candidates (report/group-by-size audio-files)
         _ (when progress-fn
             (progress-fn {:type :scan-complete
                           :count (count audio-files)
                           :candidates (count candidates)}))
         checksum-count (atom 0)
         checksum-groups (report/group-by-checksum
                           candidates
                           (when progress-fn
                             (fn [_path]
                               (let [n (swap! checksum-count inc)]
                                 (when (zero? (mod n 10))
                                   (progress-fn {:type :checksum-progress
                                                 :count n}))))))
         duplicates (report/find-duplicates checksum-groups)]
     ;; send final count
     (when progress-fn
       (progress-fn {:type :checksum-progress :count @checksum-count}))
     {:report (report/build-report audio-files duplicates)
      :duplicates duplicates})))

(defn -main
  [& args]
  (let [root-dir (first args)
        output-file (second args)]
    (when (or (nil? root-dir) (nil? output-file) (not (fs/directory? root-dir)))
      (binding [*out* *err*]
        (println "Usage: samplanager <root-directory> <output-file>")
        (println "  Scans for duplicate audio files and writes the list to output-file."))
      (System/exit 1))
    (let [send-fn (tui/run-tui {:root-dir root-dir
                                :output-file output-file})]
      (future
        (try
          (let [{:keys [report duplicates]} (find-duplicates root-dir send-fn)]
            (spit output-file (report/->json {:duplicates duplicates}))
            (send-fn {:type :done :report report :duplicates duplicates}))
          (catch Exception e
            (binding [*out* *err*]
              (println "Error:" (.getMessage e)))
            (System/exit 2)))))))
