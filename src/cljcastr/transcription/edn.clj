(ns cljcastr.transcription.edn
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]))

(defn transcript-file
  ([podcasts-root podcast-name episode-name]
   (transcript-file (fs/file podcasts-root podcast-name episode-name)))
  ([episode-dir]
   (let [episode-name (fs/file-name episode-dir)]
     (fs/file episode-dir (format "%s_transcription.edn" episode-name)))))

(defn paragraphs->transcript
  ([paragraphs]
   (paragraphs->transcript {} paragraphs))
  ([_opts paragraphs]
   (with-out-str
     (->> paragraphs
          vec
          (assoc {} :transcript)
          pprint))))

(defn transcript->paragraphs
  ([edn]
   (transcript->paragraphs {} edn))
  ([_opts edn]
   (->> edn
        edn/read-string
        :transcript)))
