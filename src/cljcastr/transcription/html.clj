(ns cljcastr.transcription.html
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [selmer.parser :as selmer]))

(defn transcript-file
  ([podcasts-root podcast-name episode-name]
   (transcript-file (fs/file podcasts-root podcast-name episode-name)))
  ([episode-dir]
   (let [episode-name (fs/file-name episode-dir)]
     (fs/file episode-dir (format "%s_transcription.html" episode-name)))))

(defn paragraphs->transcript
  ([paragraphs]
   (paragraphs->transcript {} paragraphs))
  ([{:keys [html-transcript-file episodes season-num episode-num] :as opts}
    paragraphs]
   (let [html-transcript-file (or html-transcript-file
                                  (io/resource "transcript.html"))
         season-num (or season-num 1)
         episode (when episode-num
                   (some (fn [{:keys [season number] :as episode}]
                           (and (= season-num season)
                                (= episode-num number)
                                episode))
                         episodes))]
     (selmer/render (slurp html-transcript-file)
                    (assoc opts
                           :paragraphs paragraphs
                           :episode episode)))))

(defn transcript->paragraphs
  ([html]
   (transcript->paragraphs {} html))
  ([opts html]
   (throw (UnsupportedOperationException.
           "transcript->paragraphs not yet implemented"))))
