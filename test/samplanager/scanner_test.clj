(ns samplanager.scanner-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [samplanager.scanner :as scanner]
            [babashka.fs :as fs]))

(defn- create-test-tree!
  "Creates a temp directory with a mix of audio and non-audio files.
   Returns the root path as a string."
  []
  (let [root (str (fs/create-temp-dir {:prefix "scanner-test"}))
        sub (str root "/subdir")]
    (fs/create-dirs sub)
    (spit (str root "/kick.wav") "wav-data")
    (spit (str root "/snare.mp3") "mp3-data")
    (spit (str root "/README.md") "docs")
    (spit (str root "/notes.txt") "text")
    (spit (str sub "/hat.aif") "aif-data")
    (spit (str sub "/pad.flac") "flac-data")
    (spit (str sub "/image.png") "png-data")
    (spit (str sub "/loop.WAV") "wav-upper")
    root))

(deftest scan-audio-files-test
  (testing "finds audio files recursively, skips non-audio"
    (let [root (create-test-tree!)
          results (scanner/scan-audio-files root)]
      (is (= 5 (count results)))
      (is (every? #(re-find #"\.(wav|mp3|aif|flac|WAV)$" %) results))
      (is (not-any? #(re-find #"\.(md|txt|png)$" %) results))
      (fs/delete-tree root)))

  (testing "handles case-insensitive extensions"
    (let [root (create-test-tree!)
          results (scanner/scan-audio-files root)]
      (is (some #(.endsWith % "loop.WAV") results))
      (fs/delete-tree root)))

  (testing "empty directory returns empty vector"
    (let [root (str (fs/create-temp-dir {:prefix "empty-test"}))]
      (is (= [] (scanner/scan-audio-files root)))
      (fs/delete-tree root)))

  (testing "directory with no audio files returns empty vector"
    (let [root (str (fs/create-temp-dir {:prefix "no-audio-test"}))]
      (spit (str root "/readme.md") "docs")
      (spit (str root "/data.csv") "csv")
      (is (= [] (scanner/scan-audio-files root)))
      (fs/delete-tree root))))
