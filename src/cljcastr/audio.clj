(ns cljcastr.audio
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [cljcastr.util :as util]
            [clojure.string :as str]))

(defn mp3-duration [filename]
  (-> (p/shell {:out :string}
               "ffprobe -v quiet -print_format json -show_format -show_streams"
               filename)
      :out
      (json/parse-string keyword)
      :streams
      first
      :duration
      (str/replace #"[.]\d+$" "")))

(defn track-info [filename]
  (->> (p/shell {:out :string} "vorbiscomment" filename)
       :out
       str/split-lines
       (map #(let [[k v] (str/split % #"=")] [(keyword k) v]))
       (into {})
       (merge {:filename filename})))

(defn ogg->wav [{:keys [filename] :as track} tmpdir]
  (let [out-filename (fs/file tmpdir (str/replace (fs/file-name filename)
                                                  ".ogg" ".wav"))]
    (println (format "Converting %s -> %s" filename out-filename))
    (p/shell "oggdec" "-o" out-filename filename)
    (assoc track :wav-filename out-filename)))

(defn wav->mp3 [{:keys [filename artist album title year number] :as track} tmpdir]
  (let [wav-file (fs/file tmpdir
                          (-> (fs/file-name filename)
                              (str/replace #"[.][^.]+$" ".wav")))
        mp3-file (str/replace wav-file ".wav" ".mp3")
        ffmpeg-args ["ffmpeg" "-i" wav-file
                     "-vn"  ; no video
                     "-b:a" "192k"  ; constant bitrate of 192 KB/s
                     ;; "-q:a" "2"  ; dynamic bitrate averaging 192 KB/s
                     "-y"  ; overwrite existing files without prompting
                     mp3-file]
        id3v2-args ["id3v2"
                    "-a" artist "-A" album "-t" title "-y" year "-T" number
                    mp3-file]]
    (println (format "Converting %s -> %s" wav-file mp3-file))
    (apply println (map str ffmpeg-args))
    (apply util/shell ffmpeg-args)
    (println "Writing ID3 tag")
    (apply println id3v2-args)
    (apply util/shell (map str id3v2-args))
    (assoc track
           :mp3-filename mp3-file
           :duration (mp3-duration mp3-file)
           :mp3-size (fs/size mp3-file))))

(defn list-id3-info [{:keys [base-dir podcast] :as _opts}
                     {:keys [slug audio-file number title] :as _episode}]
  (let [file (fs/file base-dir slug audio-file)]
    (util/shell "id3v2" "--list" file)))

(defn set-id3-info [{:keys [base-dir podcast] :as _opts}
                    season
                    {:keys [slug audio-file number title] :as _episode}]
  (let [file (fs/file base-dir slug audio-file)]
    (when (fs/exists? file)
      (util/shell "id3v2"
                  "--artist" (:author podcast)
                  "--album" (format "%s Season %d - %s"
                                    (:title podcast) (:number season) (:title season))
                  "--year" (str (:year season))
                  "--genre" "Podcast"
                  "--track" (str number)
                  "--song" title
                  file))))
