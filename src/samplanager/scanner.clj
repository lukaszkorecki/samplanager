(ns samplanager.scanner
  "Discovers audio files in a directory tree using babashka.fs."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mokujin.log :as log]))

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
   Skips symlinks and non-regular files.
   Optionally takes a progress-fn called periodically with the count so far."
  ([root-dir]
   (scan-audio-files root-dir nil))
  ([root-dir progress-fn]
   (log/info "starting scan" {:root-dir (str root-dir)})
   (let [count* (volatile! 0)
         results (reduce
                   (fn [acc path]
                     (if (and (fs/regular-file? path) (audio-file? path))
                       (let [s (str path)
                             n (vswap! count* inc)]
                         (when (and progress-fn (zero? (mod n 50)))
                           (progress-fn n))
                         (conj acc s))
                       acc))
                   []
                   (fs/glob root-dir "**"))]
     (when progress-fn
       (progress-fn (count results)))
     (log/info "scan complete" {:total-files (count results)
                                :extensions (frequencies (map #(str/lower-case (or (fs/extension %) "")) results))})
     results)))
