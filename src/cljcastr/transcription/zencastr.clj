(ns cljcastr.transcription.zencastr
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [cljcastr.util :refer [->map]]))

(defn transcript-file
  ([podcasts-root podcast-name episode-name]
   (transcript-file (fs/file podcasts-root podcast-name episode-name)))
  ([episode-dir]
   (let [episode-name (fs/file-name episode-dir)]
     (fs/file episode-dir (format "%s_transcription.txt" episode-name)))))

(defn transcript->paragraphs
  ([transcript]
   (transcript->paragraphs {} transcript))
  ([_opts transcript]
   (->> transcript
        str/split-lines
        (partition-by empty?)
        (remove (comp empty? first))
        (map (fn [[ts speaker text]]
               (let [[speaker text] (if text [speaker text] [nil speaker])]
                 (->map ts speaker text)))))))

(defn paragraphs->transcript
  ([paragraphs]
   (paragraphs->transcript {} paragraphs))
  ([_opts paragraphs]
   (->> paragraphs
        (map (fn [{:keys [ts speaker text]}]
               (->> (concat (when ts [ts])
                            (when speaker [speaker])
                            [text]
                            ["\n"])
                    (str/join "\n"))))
        str/join
        str/trimr)))
