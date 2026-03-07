(ns samplanager.report-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [cheshire.core :as json]
            [samplanager.report :as report]
            [babashka.fs :as fs]))

(deftest group-by-size-test
  (testing "filters out files with unique sizes, keeps size-duplicates"
    (let [root (str (fs/create-temp-dir {:prefix "size-test"}))
          f1 (str root "/a.wav")
          f2 (str root "/b.wav")
          f3 (str root "/c.wav")]
      ;; f1 and f2 have same content/size, f3 is different size
      (spit f1 "same-content")
      (spit f2 "same-content")
      (spit f3 "different-content-longer")
      (let [result (report/group-by-size [f1 f2 f3])]
        (is (= 2 (count result)))
        (is (every? #{f1 f2} result)))
      (fs/delete-tree root))))

(deftest group-by-checksum-test
  (testing "groups files by their MD5 checksum in parallel"
    (let [root (str (fs/create-temp-dir {:prefix "cksum-test"}))
          f1 (str root "/a.wav")
          f2 (str root "/b.wav")
          f3 (str root "/c.wav")]
      (spit f1 "identical")
      (spit f2 "identical")
      (spit f3 "unique!!")
      (let [groups (report/group-by-checksum [f1 f2 f3])]
        ;; two distinct checksums
        (is (= 2 (count groups)))
        ;; one group has 2 files, the other has 1
        (is (match? #{1 2} (set (map count (vals groups))))))
      (fs/delete-tree root))))

(deftest find-duplicates-test
  (testing "returns only groups with 2+ files"
    (let [groups {"hash1" ["a" "b"]
                  "hash2" ["c"]
                  "hash3" ["d" "e" "f"]}]
      (is (match? [["a" "b"] ["d" "e" "f"]]
                  (report/find-duplicates groups))))))

(deftest build-report-test
  (testing "produces correct counts and structure"
    (let [all-files ["a" "b" "c" "d" "e"]
          dups [["a" "b"] ["c" "d" "e"]]
          result (report/build-report all-files dups)]
      (is (match? {:found-files-count 5
                   :duplicate-files-count 5
                   :duplicates [["a" "b"] ["c" "d" "e"]]}
                  result)))))

(deftest report->json-test
  (testing "produces valid JSON with expected keys"
    (let [report {:found-files-count 10
                  :duplicate-files-count 4
                  :duplicates [["a" "b"] ["c" "d"]]}
          json-str (report/report->json report)
          parsed (json/parse-string json-str true)]
      (is (match? {:found-files-count 10
                   :duplicate-files-count 4
                   :duplicates [["a" "b"] ["c" "d"]]}
                  parsed)))))
