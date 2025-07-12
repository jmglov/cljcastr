(ns cljcastr.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))

(defn ->int
  "Attempts to convert x (assumed to be a string or number) to an integer. Throws
   the underlying exception on failure."
  [x]
  (if (string? x)
    (Integer/parseInt x)
    (int x)))

(defn strip-path [n path]
  (let [path-components (fs/components path)]
    (->> (or (not-empty (drop n path-components))
             (last path-components))
         (str/join fs/file-separator))))

(defn modified-since?
  "Returns truthy if file1 has been modified more recently than file2."
  [file1 file2]
  (not-empty (fs/modified-since file2 file1)))

(defn relative-filename [base-path filename]
  (let [base-path (format "%s/" (str base-path))
        filename (str filename)]
    (str/replace-first filename base-path "")))

(defn copy-modified!
  "Copies files from src-dir that have been modified more recently than dst-dir
   to dst-dir/src-filename, where src-filename is the filename relative to
   src-dir."
  [src-dir dst-dir]
  (let [files (->> (fs/glob src-dir "**")
                   (filter fs/regular-file?)
                   (map (comp str (partial fs/relativize src-dir)))
                   (filter #(modified-since? (fs/file src-dir %)
                                             (fs/file dst-dir %))))]
    (doseq [file files
            :let [src (fs/file src-dir file)
                  dst (fs/file dst-dir file)]]
      (fs/create-dirs (fs/parent dst))
      (fs/copy src dst {:replace-existing true}))
    files))

(defn ->snake_case [obj]
  (cond
    (keyword? obj)
    (-> obj name (str/replace #"-" "_") keyword)

    (vector? obj)
    (->> obj
         (map ->snake_case)
         (into []))

    (sequential? obj)
    (map ->snake_case obj)

    (map? obj)
    (->> obj
         (map (fn [[k v]] [(->snake_case k) (->snake_case v)]))
         (into {}))

    :else
    obj))
