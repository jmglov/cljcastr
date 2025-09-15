(ns cljcastr.rss
  (:require [babashka.fs :as fs]
            [cljcastr.audio :as audio]
            [cljcastr.template :as template]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [selmer.parser :as selmer])
  (:import (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(def dt-formatter
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss xxxx"))

(defn format-dt [dt]
  (.format dt dt-formatter))

(defn now []
  (format-dt (ZonedDateTime/now java.time.ZoneOffset/UTC)))

(defn kebab->snake [k]
  (-> k name (str/replace "-" "-")))

(defn html->single-line [html]
  (-> html
      (str/replace #"\n\s*<" "<")
      (str/replace #">\n\s*" ">")
      (str/replace #"\n\s+" " ")))

(defn update-episode [{:keys [base-dir out-dir
                              description-epilogue bonus-numbers?] :as opts}
                      {:keys [bonus-number] :as episode}]
  (let [episode (if (and bonus-numbers? bonus-number)
                  (assoc episode :number bonus-number)
                  episode)
        {:keys [audio-file description path] :as episode}
        (template/expand-context 5 episode opts)
        filename (fs/file base-dir out-dir path audio-file)
        description (->> [description description-epilogue]
                         (map str/trim)
                         (str/join "\n")
                         html->single-line)]
    (assoc episode
           :audio-filesize (fs/size filename)
           :duration (audio/mp3-duration filename)
           :description (selmer/render description
                                       (assoc opts :episode episode)))))

(defn update-episodes [{:keys [podcast] :as opts}]
  (let [sort-fn (fn [ep1 ep2]
                  (if (= (:type podcast) "Serial")
                    (compare (or (:number ep1) (:bonus-number ep1) 0)
                             (or (:number ep2) (:bonus-number ep2) 0))
                    (compare (or (:number ep2) 0)
                             (or (:number ep1) 0))))]
    (-> opts
        (assoc :datetime-now (now))
        (update :episodes
                (fn [episodes]
                  (->> episodes
                       (sort sort-fn)
                       (filter #(or (:include-previews opts)
                                    (not (:preview? %))))
                       (map (partial update-episode opts)))))
        template/expand-context)))

(defn podcast-feed [opts]
  (let [template (-> (io/resource "podcast-feed.rss") slurp)]
    (selmer/render template (update-episodes opts))))
