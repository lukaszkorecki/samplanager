(ns samplanager.checksum-test
  (:require [clojure.test :refer [deftest testing is]]
            [samplanager.checksum :as checksum]
            [babashka.fs :as fs]))

(deftest md5-hex-test
  (testing "empty file produces known MD5"
    (let [tmp (str (fs/create-temp-file {:prefix "checksum-test" :suffix ".bin"}))]
      (spit tmp "")
      (is (= "d41d8cd98f00b204e9800998ecf8427e"
             (checksum/md5-hex tmp)))
      (fs/delete tmp)))

  (testing "known content produces expected MD5"
    (let [tmp (str (fs/create-temp-file {:prefix "checksum-test" :suffix ".bin"}))]
      ;; MD5 of "hello\n" = b1946ac92492d2347c6235b4d2611184
      (spit tmp "hello\n")
      (is (= "b1946ac92492d2347c6235b4d2611184"
             (checksum/md5-hex tmp)))
      (fs/delete tmp)))

  (testing "identical content in different files produces same hash"
    (let [tmp1 (str (fs/create-temp-file {:prefix "dup1" :suffix ".wav"}))
          tmp2 (str (fs/create-temp-file {:prefix "dup2" :suffix ".wav"}))
          content (apply str (repeat 1000 "sample-data-block"))]
      (spit tmp1 content)
      (spit tmp2 content)
      (is (= (checksum/md5-hex tmp1)
             (checksum/md5-hex tmp2)))
      (fs/delete tmp1)
      (fs/delete tmp2)))

  (testing "different content produces different hashes"
    (let [tmp1 (str (fs/create-temp-file {:prefix "diff1" :suffix ".wav"}))
          tmp2 (str (fs/create-temp-file {:prefix "diff2" :suffix ".wav"}))]
      (spit tmp1 "content-a")
      (spit tmp2 "content-b")
      (is (not= (checksum/md5-hex tmp1)
                (checksum/md5-hex tmp2)))
      (fs/delete tmp1)
      (fs/delete tmp2))))
