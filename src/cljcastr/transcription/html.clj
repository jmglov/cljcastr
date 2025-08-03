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
  ([{:keys [html-transcript-file] :as opts} paragraphs]
   (let [html-transcript-file (or html-transcript-file
                                  (io/resource "transcript.html"))]
     (selmer/render (slurp html-transcript-file)
                    (assoc opts :paragraphs paragraphs)))))

(defn transcript->paragraphs [html]
  (throw (UnsupportedOperationException.
          "transcript->paragraphs not yet implemented")))
