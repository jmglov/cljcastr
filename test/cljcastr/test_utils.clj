(ns cljcastr.test-utils
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [cljcastr.util :as util])
  (:import (java.util UUID)))

(def test-dir ".test")

(defn test-dir-fixture [test-fn]
  (with-out-str
    (test-fn)
    (fs/delete-tree test-dir)))

(defn tmp-dir [dir-name]
  (fs/file test-dir
           (format "cljcastr-test-%s-%s" dir-name (str (UUID/randomUUID)))))

(defn spit' [filename content]
  (fs/create-dirs (fs/parent filename))
  (spit filename content))

(defmacro with-dirs
  "dirs is a seq of directory names; e.g. [cache-dir out-dir]"
  [dirs & body]
  (let [binding-form# (mapcat (fn [dir] [dir `(tmp-dir ~(str dir))]) dirs)]
    `(let [~@binding-form#]
       ~@body)))
