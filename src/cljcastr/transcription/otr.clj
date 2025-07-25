(ns cljcastr.transcription.otr
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [cljcastr.time :as time]
            [cljcastr.util :refer [->map]]
            [clojure.string :as str]))

(defn transcript-file
  ([podcasts-root podcast-name episode-name]
   (transcript-file (fs/file podcasts-root podcast-name episode-name)))
  ([episode-dir]
   (let [episode-name (fs/file-name episode-dir)]
     (fs/file episode-dir (format "%s_transcription.otr" episode-name)))))

(defn- line->paragraph [p-text]
  (let [[_ ts speaker text]
        (->> p-text
             (re-seq #"^<span.+class=\"timestamp\".*>(.+)</span>.*<b>\s*(\S.*)\s*</b>:\s*([^<]+)<.+$")
             first)

        [ts text]
        (if text
          [ts text]
          (->> p-text
               (re-seq #"^<span.+class=\"timestamp\".*>(.+)</span>\s*([^<]+)<.+$")
               first
               (drop 1)))]
    (->map ts speaker text)))

(defn transcript->paragraphs [transcript]
  (->> (-> (json/parse-string transcript keyword)
           :text
           (str/split #"<p>"))
       (remove empty?)
       (map line->paragraph)))

(defn paragraphs->transcript [paragraphs]
  (->> paragraphs
       (map (fn [{:keys [ts speaker text]}]
              (format "<p>%s%s%s</p>"
                      (if-let [sec (time/ts->sec ts)]
                        (let [[hh mm ss] (str/split ts #":")
                              str-ts (if (and (not= "00" hh) ss)
                                       (format "%s:%s:%s" hh mm ss)
                                       (format "%s:%s" mm ss))]
                          (format "<span class=\"timestamp\" data-timestamp=\"%.2f\">%s</span>"
                                  (time/ts->sec ts) (time/sec->ts sec)))
                        "")
                      (if speaker (format "<b>%s</b>: " speaker) "")
                      text)))
       (str/join "\n")
       (assoc {} :text)
       json/generate-string))
