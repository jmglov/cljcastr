(ns cljcastr.util-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [babashka.fs :as fs]
            [cljcastr.util :as util])
  (:import (java.util UUID)))

(def test-dir ".test")

(use-fixtures :each
  (fn [test-fn]
    (with-out-str
      (test-fn)
      (fs/delete-tree test-dir))))

(defn- tmp-dir [dir-name]
  (fs/file test-dir
           (format "cljcastr-test-%s-%s" dir-name (str (UUID/randomUUID)))))

(defn- spit' [filename content]
  (fs/create-dirs (fs/parent filename))
  (spit filename content))

(defmacro with-dirs
  "dirs is a seq of directory names; e.g. [cache-dir out-dir]"
  [dirs & body]
  (let [binding-form# (mapcat (fn [dir] [dir `(tmp-dir ~(str dir))]) dirs)]
    `(let [~@binding-form#]
       ~@body)))

(deftest ->map-test
  (testing "happy path"
    (let [[a b c] [1 2 3]]
      (is (= {:a 1, :b 2, :c 3}
             (util/->map a b c))))))

(deftest ->int-test

  (testing "int string"
    (is (= 12
           (util/->int "12"))))

  (testing "number"
    (is (= 12
           (util/->int 12))))

  (testing "floating point number"
    (is (= 12
           (util/->int 12.34))))

  (testing "floating point string throws exception"
    (is (thrown? Exception
                 (util/->int "12.34"))))

  (testing "anything else throws exception"
    (is (thrown? Exception
                 (util/->int [12]))))

  )

(deftest modified-since-test

  (testing "newer file to file"
    (with-dirs [tmpdir]
      (fs/create-dirs tmpdir)
      (let [file1 (fs/file tmpdir "file1")
            file2 (fs/file tmpdir "file2")]
        (spit' file1 "Stuff")
        (Thread/sleep 42)
        (spit' file2 "Things")
        (is (= [(str (fs/file tmpdir "file2"))]
               (->> (util/modified-since? file2 file1)
                    (map (comp str fs/file))))))))

  (testing "older file to file"
    (with-dirs [tmpdir]
      (fs/create-dirs tmpdir)
      (let [file1 (fs/file tmpdir "file1")
            file2 (fs/file tmpdir "file2")]
        (spit' file1 "Stuff")
        (Thread/sleep 42)
        (spit' file2 "Things")
        (is (= []
               (->> (util/modified-since? file1 file2)
                    (map (comp str fs/file))))))))

  (testing "newer file to dir"
    (with-dirs [src-dir target-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs target-dir)
      (let [file1 (fs/file src-dir "file1")]
        (Thread/sleep 42)
        (spit' file1 "Stuff")
        (is (= [(str (fs/file src-dir "file1"))]
               (->> (util/modified-since? file1 target-dir)
                    (map (comp str fs/file))))))))

  (testing "older file to dir"
    (with-dirs [src-dir target-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs target-dir)
      (let [file1 (fs/file src-dir "file1")
            target1 (fs/file target-dir "file1")]
        (spit' file1 "Stuff")
        (Thread/sleep 42)
        (spit' target1 "Things")
        (is (= []
               (->> (util/modified-since? file1 target-dir)
                    (map (comp str fs/file))))))))

  (testing "some newer files to dir"
    (with-dirs [src-dir target-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs target-dir)
      (let [file1 (fs/file src-dir "file1")
            target1 (fs/file target-dir "file1")
            file2 (fs/file src-dir "file2")
            target2 (fs/file target-dir "file2")
            file3 (fs/file src-dir "file3")
            target3 (fs/file target-dir "file3")]
        (Thread/sleep 42)
        (spit' file1 "Stuff")
        (spit' file2 "Things")
        (spit' file3 "Stuff and things")
        (spit' target3 "Whatever")
        (Thread/sleep 42)
        (spit' file1 "More stuff")
        (spit' file2 "More things")
        (is (= [(str file1) (str file2)]
               (->> (util/modified-since? [file1 file2 file3] target-dir)
                    (map (comp str fs/file))))))))

  (testing "no newer files to dir"
    (with-dirs [src-dir target-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs target-dir)
      (let [file1 (fs/file src-dir "file1")
            target1 (fs/file target-dir "file1")
            file2 (fs/file src-dir "file2")
            target2 (fs/file target-dir "file2")
            file3 (fs/file src-dir "file3")
            target3 (fs/file target-dir "file3")]
        (Thread/sleep 42)
        (spit' file1 "Stuff")
        (spit' file2 "Things")
        (spit' file3 "Stuff and things")
        (Thread/sleep 42)
        (spit' target3 "Whatever")
        (is (= []
               (->> (util/modified-since? [file1 file2 file3] target-dir)
                    (map (comp str fs/file))))))))

  (testing "dir with some newer files to dir"
    (with-dirs [src-dir target-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs target-dir)
      (let [file1 (fs/file src-dir "file1")
            target1 (fs/file target-dir "file1")
            file2 (fs/file src-dir "file2")
            target2 (fs/file target-dir "file2")
            file3 (fs/file src-dir "file3")
            target3 (fs/file target-dir "file3")]
        (Thread/sleep 42)
        (spit' file1 "Stuff")
        (spit' file2 "Things")
        (spit' file3 "Stuff and things")
        (spit' target3 "Whatever")
        (Thread/sleep 42)
        (spit' file1 "More stuff")
        (spit' file2 "More things")
        (is (= [(str file1) (str file2)]
               (->> (util/modified-since? src-dir target-dir)
                    (map (comp str fs/file))
                    sort))))))

  )

(deftest relative-filename-test

  (testing "relative filename"
    (let [base-path "/some/path/or/other"
          filename (fs/file base-path "a/b/c")]
      (is (= "a/b/c"
             (util/relative-filename base-path filename)))))

  )

(deftest copy-modified-test

  (testing "happy path"
    (with-dirs [src-dir dst-dir]
      (fs/create-dirs src-dir)
      (fs/create-dirs dst-dir)
      (let [new-file (fs/file src-dir "new" "file")
            new-file-dst (fs/file dst-dir "new" "file")
            modified-file (fs/file src-dir "modified" "file")
            modified-file-dst (fs/file dst-dir "modified" "file")
            boring-file (fs/file src-dir "boring" "file")
            boring-file-dst (fs/file dst-dir "boring" "file")]
        (spit' new-file "Stuff")
        (spit' modified-file-dst "Things")
        (spit' boring-file "Boring")
        (Thread/sleep 42)
        (spit' modified-file "More things")
        (spit' boring-file-dst "Stuff and things")
        (is (= (->> [["new" "file"]
                     ["modified" "file"]]
                    (map (comp str (partial apply fs/file)))
                    sort)
               (->> (util/copy-modified! src-dir dst-dir) sort)))
        (is (= "Stuff" (slurp new-file-dst)))
        (is (= "More things" (slurp modified-file-dst)))
        (is (= "Stuff and things" (slurp boring-file-dst))))))

  )

(deftest camel-snake-kebab

  (testing "->snake_case"
    (is (= :foo_bar_baz (util/->snake_case :foo-bar-baz)))
    (is (= :foo_bar_baz (util/->snake_case :foo_bar_baz)))

    (is (= {:foo_bar 1, :baz_blah 2}
           (util/->snake_case {:foo-bar 1, :baz-blah 2})))

    (is (= [:foo_bar "baz-blah" 42]
           (util/->snake_case [:foo-bar "baz-blah" 42])))

    (is (= {:foo_bar ["baz-blah" {:and_so_on {:for_ever ["and-ever"]}}]}
           (util/->snake_case
            {:foo-bar ["baz-blah" {:and-so-on {:for-ever ["and-ever"]}}]})))

    (is (= "foo-bar" (util/->snake_case "foo-bar")))
    (is (= 42 (util/->snake_case 42)))))
