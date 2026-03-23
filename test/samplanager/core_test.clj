(ns samplanager.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [samplanager.core :as core]
            [babashka.fs :as fs]))

(defn- create-sample-library!
  "Creates a temp directory simulating a sample library with known duplicates."
  []
  (let [root (str (fs/create-temp-dir {:prefix "core-test"}))
        splice (str root "/splice")
        gumroad (str root "/gumroad")
        legacy (str root "/legacy")]
    (fs/create-dirs splice)
    (fs/create-dirs gumroad)
    (fs/create-dirs legacy)
    ;; Duplicate kick across splice and gumroad
    (spit (str splice "/kick-808.wav") "kick-sample-data")
    (spit (str gumroad "/808-kick.wav") "kick-sample-data")
    ;; Duplicate hat across all three
    (spit (str splice "/hihat-closed.wav") "hihat-data")
    (spit (str gumroad "/closed-hh.wav") "hihat-data")
    (spit (str legacy "/hat.wav") "hihat-data")
    ;; Unique files
    (spit (str splice "/snare-tight.wav") "unique-snare-data!!")
    (spit (str legacy "/pad-ambient.flac") "unique-pad-sample")
    ;; Non-audio file (should be ignored)
    (spit (str root "/README.txt") "sample library notes")
    root))

(deftest find-duplicates-test
  (testing "end-to-end: detects duplicates across subdirectories"
    (let [root (create-sample-library!)
          {:keys [report duplicates]} (core/find-duplicates [root])]
      (is (match? {:found-files-count 7
                   :duplicate-files-count 5
                   :duplicate-groups-count 2
                   :total-size pos-int?
                   :total-size-human string?
                   :duplicate-size pos-int?
                   :duplicate-size-human string?
                   :dirs-by-duplicates #(pos? (count %))}
                  report))
      (is (= 2 (count duplicates)))
      (let [sizes (set (map count duplicates))]
        (is (= #{2 3} sizes)))
      (fs/delete-tree root)))

  (testing "no duplicates returns empty duplicates list"
    (let [root (str (fs/create-temp-dir {:prefix "no-dups-test"}))]
      (spit (str root "/kick.wav") "unique-kick")
      (spit (str root "/snare.wav") "unique-snare-data")
      (let [{:keys [report duplicates]} (core/find-duplicates [root])]
        (is (match? {:found-files-count 2
                     :duplicate-files-count 0
                     :duplicate-groups-count 0
                     :total-size pos-int?
                     :duplicate-size zero?}
                    report))
        (is (= [] duplicates)))
      (fs/delete-tree root)))

  (testing "multi-dir: finds duplicates across separate root directories"
    (let [dir-a (str (fs/create-temp-dir {:prefix "multi-a"}))
          dir-b (str (fs/create-temp-dir {:prefix "multi-b"}))]
      ;; Same file content in two separate roots
      (spit (str dir-a "/kick.wav") "shared-kick-data")
      (spit (str dir-b "/kick-copy.wav") "shared-kick-data")
      ;; Unique file in each
      (spit (str dir-a "/snare.wav") "unique-snare-aaa")
      (spit (str dir-b "/hat.wav") "unique-hat-bbb!!!")
      (let [{:keys [report duplicates]} (core/find-duplicates [dir-a dir-b])]
        (is (match? {:found-files-count 4
                     :duplicate-files-count 2
                     :duplicate-groups-count 1}
                    report))
        (is (= 1 (count duplicates)))
        (is (= 2 (count (first duplicates)))))
      (fs/delete-tree dir-a)
      (fs/delete-tree dir-b))))
