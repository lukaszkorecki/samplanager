(ns samplanager.scanner
  "Discovers audio files in a directory tree using babashka.fs."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def audio-extensions
  "Set of supported audio file extensions (lowercase, without dot)."
  #{"wav" "aif" "aiff" "mp3" "flac" "ogg"})

(defn audio-file?
  "Returns true if the path has a recognized audio file extension."
  [path]
  (let [ext (some-> (fs/extension path) str/lower-case)]
    (contains? audio-extensions ext)))

(defn scan-audio-files
  "Recursively scans root-dir and returns a vector of audio file paths as strings.
   Skips symlinks and non-regular files."
  [root-dir]
  (->> (fs/glob root-dir "**")
       (filter fs/regular-file?)
       (filter audio-file?)
       (mapv str)))
