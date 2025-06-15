(ns cljcastr.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))

(comment

  (let [[a b c] [1 2 3]]
    (->map a b c))
  ;; => {:a 1, :b 2, :c 3}

  )

(defn modified-since?
  "Returns truthy if file1 has been modified since file2"
  [file1 file2]
  (not-empty (fs/modified-since file2 file1)))

(comment

  (let [tmpdir (fs/temp-dir)
        file1 (fs/file tmpdir "file1")
        file2 (fs/file tmpdir "file2")]
    (spit file1 "Stuff")
    (Thread/sleep 42)
    (spit file2 "Things")
    (modified-since? file2 file1))
  ;; => (#object[sun.nio.fs.UnixPath 0x10435010 "/tmp/file2"])

  )

(defn relative-filename [base-path filename]
  (let [base-path (format "%s/" (str base-path))
        filename (str filename)]
    (str/replace-first filename base-path "")))

(comment

  (let [base-path "/some/path/or/other"
        filename (fs/file base-path "a/b/c")]
    (relative-filename base-path filename))
  ;; => "a/b/c"

  )
