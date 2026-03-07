(ns samplanager.checksum
  "Computes MD5 checksums for files using JDK MessageDigest.
   Reads files in chunks to handle large audio files without
   loading them entirely into memory."
  (:import [java.security MessageDigest]
           [java.io FileInputStream]))

(defn md5-hex
  "Computes the MD5 hex digest of the file at the given path.
   Reads in 8KB chunks for memory efficiency."
  [file-path]
  (let [digest (MessageDigest/getInstance "MD5")
        buffer (byte-array 8192)]
    (with-open [fis (FileInputStream. (str file-path))]
      (loop []
        (let [n (.read fis buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (let [hash-bytes (.digest digest)]
      (apply str (map #(format "%02x" %) hash-bytes)))))
