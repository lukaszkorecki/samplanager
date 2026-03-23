(ns samplanager.report
  "Groups files by size and checksum to find duplicates.
   Produces JSON reports and computes stats."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [samplanager.log :as log]
            [samplanager.checksum :as checksum]))

(defn group-by-size
  "Returns only file paths that share a size with at least one other file.
   Files with unique sizes cannot be duplicates, so we skip checksumming them."
  [file-paths]
  (log/debug "grouping by size" {:file-count (count file-paths)})
  (let [groups (group-by #(fs/size %) file-paths)
        candidates (->> (vals groups)
                        (filter #(> (count %) 1))
                        (apply concat)
                        vec)]
    (log/debug "size grouping complete" {:input (count file-paths)
                                         :candidates (count candidates)
                                         :skipped (- (count file-paths) (count candidates))})
    candidates))

(defn checksum-file
  "Returns a [path checksum] pair for a single file."
  [path]
  [path (checksum/md5-hex path)])

(defn group-by-checksum
  "Computes MD5 checksums in parallel and groups files by checksum.
   Optionally takes a progress-fn called with each path after checksumming.
   Returns a map of {checksum [path1 path2 ...]}."
  ([file-paths]
   (group-by-checksum file-paths nil))
  ([file-paths progress-fn]
   (log/debug "starting parallel checksum" {:file-count (count file-paths)})
   (let [result (->> file-paths
                     (pmap (fn [path]
                             (let [result (checksum-file path)]
                               (when progress-fn (progress-fn path))
                               result)))
                     (reduce
                      (fn [acc [path hash]]
                        (update acc hash (fnil conj []) path))
                      {}))]
     (log/debug "checksum grouping complete" {:unique-checksums (count result)})
     result)))

(defn find-duplicates
  "Returns groups of files that share the same checksum (2+ files per group).
   Sorted by first path for deterministic output."
  [checksum-groups]
  (let [dups (->> (vals checksum-groups)
                  (filter #(> (count %) 1))
                  (sort-by first)
                  vec)]
    (log/debug "duplicates found" {:groups (count dups)
                                   :total-files (reduce + 0 (map count dups))})
    dups))

(defn human-size
  "Formats byte count as human-readable string (KB, MB, GB)."
  [bytes]
  (cond
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ (double bytes) 1024))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ (double bytes) (* 1024 1024)))
    :else (format "%.2f GB" (/ (double bytes) (* 1024 1024 1024)))))

(defn total-file-size
  "Computes total size in bytes for a seq of file paths."
  [file-paths]
  (reduce + 0 (map #(fs/size %) file-paths)))

(defn dir-duplicate-counts
  "Returns a sorted list of [directory count] pairs showing which
   directories contain the most duplicate files."
  [duplicate-groups]
  (let [all-dup-paths (mapcat identity duplicate-groups)]
    (->> all-dup-paths
         (map #(str (fs/parent %)))
         frequencies
         (sort-by val >)
         vec)))

(defn build-report
  "Builds the final report map with counts and stats."
  [all-files duplicate-groups]
  (let [all-dup-paths (vec (mapcat identity duplicate-groups))
        total-size (total-file-size all-files)
        dup-size (total-file-size all-dup-paths)]
    {:found-files-count (count all-files)
     :duplicate-files-count (count all-dup-paths)
     :duplicate-groups-count (count duplicate-groups)
     :total-size total-size
     :total-size-human (human-size total-size)
     :duplicate-size dup-size
     :duplicate-size-human (human-size dup-size)
     :dirs-by-duplicates (dir-duplicate-counts duplicate-groups)}))

(defn ->json
  "Serializes a map to pretty-printed JSON."
  [m]
  (json/generate-string m {:pretty true}))
