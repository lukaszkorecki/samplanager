(ns samplanager.report
  "Groups files by size and checksum to find duplicates.
   Produces JSON reports."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [samplanager.checksum :as checksum]))

(defn group-by-size
  "Returns only file paths that share a size with at least one other file.
   Files with unique sizes cannot be duplicates, so we skip checksumming them."
  [file-paths]
  (->> file-paths
       (group-by #(fs/size %))
       vals
       (filter #(> (count %) 1))
       (apply concat)
       vec))

(defn checksum-file
  "Returns a [path checksum] pair for a single file."
  [path]
  [path (checksum/md5-hex path)])

(defn group-by-checksum
  "Computes MD5 checksums in parallel and groups files by checksum.
   Returns a map of {checksum [path1 path2 ...]}."
  [file-paths]
  (->> file-paths
       (pmap checksum-file)
       (reduce
        (fn [acc [path hash]]
          (update acc hash (fnil conj []) path))
        {})))

(defn find-duplicates
  "Returns groups of files that share the same checksum (2+ files per group).
   Sorted by first path for deterministic output."
  [checksum-groups]
  (->> (vals checksum-groups)
       (filter #(> (count %) 1))
       (sort-by first)
       vec))

(defn build-report
  "Builds the final report map with counts only (no file list)."
  [all-files duplicate-groups]
  {:found-files-count (count all-files)
   :duplicate-files-count (reduce + 0 (map count duplicate-groups))
   :duplicate-groups-count (count duplicate-groups)})

(defn ->json
  "Serializes a map to pretty-printed JSON."
  [m]
  (json/generate-string m {:pretty true}))
