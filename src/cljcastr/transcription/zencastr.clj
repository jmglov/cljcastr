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
        (map (fn [[line1 line2 line3 :as lines]]
               (cond
                 (= (count lines) 3)
                 {:ts line1, :speaker line2, :text line3}

                 (re-matches #"(\d+:)?(\d{2}:)(\d{2})([.]\d+)?" line1)
                 {:ts line1, :text line2}

                 (= (count lines) 2)
                 {:speaker line1, :text line2}

                 :else
                 {:text line1}))))))

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
