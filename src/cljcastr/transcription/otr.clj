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

(defn- line->paragraph [input-line p-text]
  (try
    (let [input-line (inc input-line)
          p-text (-> p-text
                     str/trim
                     (str/replace #"</p>$" "")
                     (str/replace #"<br\s?/>" "")
                     (str/replace #"<b>\s*</b>" "")
                     (str/replace #"</?span>" ""))

          ;; <span class="timestamp" data-timestamp="46.00">00:46</span><b>Foo Bar</b>: Stuff and things
          [_ ts speaker text]
          (->> p-text
               (re-seq #"^.*<span.+class=\"timestamp\".*>([0-9:.]+)\s*<b>(.+)</b>\s*:(.+)$")
               first)

          ;; <span class="timestamp" data-timestamp="2.00">00:02</span>[Theme music starts]
          [ts text]
          (if text
            [ts text]
            (->> p-text
                 (re-seq #"^.*<span.+class=\"timestamp\".*>([0-9:.]+)([^0-9].*)$")
                 first
                 (drop 1)))

          ;; <b>Josh</b>: Ray, we need to talk about trust.
          [speaker text]
          (if text
            [speaker text]
            (->> p-text
                 (re-seq #"^.*<b>(.+)</b>:(.+)$")
                 first
                 (drop 1)))

          ts (or ts "99:99:99.999")
          speaker (when speaker (str/trim speaker))
          text (when text (str/trim text))]
      (->map ts speaker text input-line))
    (catch Exception e
      (throw (ex-info (.getMessage e) {::line input-line, ::text p-text} e)))))

(defn transcript->paragraphs
  ([transcript]
   (transcript->paragraphs {} transcript))
  ([_opts transcript]
   (->> (-> (json/parse-string transcript keyword)
            :text
            (str/split #"<p>"))
        (remove empty?)
        (map-indexed line->paragraph))))

(defn paragraphs->transcript
  ([paragraphs]
   (paragraphs->transcript {} paragraphs))
  ([{:keys [otr-html-only] :as _opts} paragraphs]
   (let [html
         (->> paragraphs
              (map (fn [{:keys [ts speaker text]}]
                     (format "<p>%s%s%s</p>"
                             (if-let [sec (time/ts->sec ts)]
                               (let [[hh mm ss] (str/split ts #":")
                                     str-ts (if (and (not= "00" hh) ss)
                                              (format "%s:%s:%s" hh mm ss)
                                              (format "%s:%s" mm ss))]
                                 (format "<span class=\"timestamp\" data-timestamp=\"%.2f\">%s</span>"
                                         (time/ts->sec ts)
                                         (str/replace (time/sec->ts sec) #"[.][0-9]+$" "")))
                               "")
                             (if speaker (format "<b>%s</b>: " speaker) "")
                             text)))
              (str/join "\n"))]
     (if otr-html-only
       html
       (->> html
            (assoc {} :text)
            json/generate-string)))))
