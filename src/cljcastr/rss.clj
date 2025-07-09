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

(defn update-episode [{:keys [base-dir out-dir description-epilogue] :as opts}
                      episode]
  (let [{:keys [audio-file description path] :as episode}
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
                    (compare (or (:number ep1) 0)
                             (or (:number ep2) 0))
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

(comment

  (def opts
    {:socials
     [{:name "BlueSky",
       :url "https://bsky.app/profile/politechs.bsky.social",
       :image "/img/bluesky-logo.svg",
       :image-alt "BlueSky logo"}
      {:name "Mastodon",
       :url "https://mastodon.social/@politechs",
       :image "/img/mastodon.svg",
       :image-alt "Mastodon logo"}],
     :preview-image "/img/politechs-preview.jpg",
     :podcast
     {:description
      "A podcast exploring the inherent political nature of technology",
      :email "politechs@politechs.dev",
      :explicit true,
      :copyright "All rights reserved, Politechs",
      :type "Serial",
      :title "Politechs",
      :author "Politechs",
      :categories
      [{:text "Technology"} {:text "News", :subcategories [{:text "Politics"}]}],
      :language "en",
      :image-alt
      "Podcast logo: silhouettes of two people sitting at a table with laptops over a background of flames",
      :image "/img/politechs-cover.jpg"},
     :website-bucket "politechs.dev",
     :episodes-dir "episodes",
     :out-dir "public",
     :s3-bucket "-",
     :base-url "https://{{domain}}",
     :distribution-id "E3P60YCZZEU9R6",
     :description-epilogue
     "\n<p class=\"soundcljoud-hidden\">\n  To view full show notes, including transcripts, please visit the\n  <a href=\"{{base-url}}{{episode.path}}/\">episode page</a>. Be sure to follow us on {% for social in socials %}{% if not forloop.first %}{% if forloop.length > 2%},{% endif %} {% endif %}{% if forloop.last %}and {% endif %}<a href=\"{{social.url}}\">{{social.name}}</a>{% endfor %}!\n</p>\n<p>\n  Cover art by <a href=\"https://anyakjordan.com/\">Anya K. Jordan</a>\n  <a href=\"https://bsky.app/profile/anyakjordan.bsky.social\">@anyakjordan.bsky.social</a>\n</p>\n<p>\n  Theme music by <a href=\"https://soundcloud.com/ptzery\">Ptzery</a>\n</p>",
     :episodes
     [{:description
       "\n<p>\n  Since the invention of the wheel, automation has both substituted and\n  complemented labour; machines replaced humans at some lower-paying jobs, but\n  this was compensated by the creation of new, higher-paying jobs; in other\n  words: tech workers. In recent years, there has been a desire from certain\n  high-profile tech companies for an \"apolitical workplace\". This in itself is\n  a political act; an act designed to suppress the power of tech workers and\n  reinforce the status quo. They know that politics is inextricable from tech.\n  And now so do you. Welcome to Politechs.\n</p>\n<p>\n  In Season 1 of Politechs, it's time to talk about AI: what it is, what it can\n  and can't do, what makes it dangerous, and why we as tech workers must not\n  buy into the inevitability narrative; why we must resist, and how we can\n  resist.\n</p>\n<p class=\"soundcljoud-hidden\">\n  To view full show notes, including transcripts, please visit the\n  <a href=\"{{base-url}}{{episode.path}}/\">episode page</a>.\n</p>\n<p>\n  Cover art by <a href=\"https://anyakjordan.com/\">Anya K. Jordan</a>\n  <a href=\"https://bsky.app/profile/anyakjordan.bsky.social\">@anyakjordan.bsky.social</a>\n</p>\n<p>\n  Theme music by <a href=\"https://soundcloud.com/ptzery\">Ptzery</a>\n</p>",
       :path "{{episodes-dir}}/{{slug}}",
       :transcript-file "{{slug}}_transcription.txt",
       :date "Mon, 7 Jul 2025 00:00:00 +0000",
       :slug "ai-{{number|number-format:%02d}}-trailer",
       :audio-file "{{slug}}.mp3",
       :number 0,
       :mime-type "audio/mpeg",
       :explicit false,
       :preview? true,
       :type "Trailer",
       :title "Trailer",
       :summary "{{podcast.description}}. Season 1: AI."}],
     :stylesheets ["/css/soundcljoud.css" "/css/main.css"],
     :base-dir "/home/jmglov/Documents/projects/politechs",
     :s3-media-path "-",
     :domain "politechs.dev",
     :feed-file "/feed.rss",
     :podcast-feed-template "templates/podcast-feed.rss",
     :episode-template "templates/episode-page.html"})

  (podcast-feed (assoc opts :include-previews true))
  ;; => "<?xml version='1.0' encoding='UTF-8'?>\n<rss version=\"2.0\"\n     xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\"\n     xmlns:atom=\"http://www.w3.org/2005/Atom\">\n  <channel>\n    <title>Politechs</title>\n    <description>A podcast exploring the inherent political nature of technology</description>\n    <itunes:image href=\"https://politechs.dev/img/politechs-cover.jpg\"/>\n    <language>en</language>\n    <itunes:explicit>true</itunes:explicit>\n\n    <itunes:category text=\"Technology\">\n\n    </itunes:category>\n\n    <itunes:category text=\"News\">\n\n      <itunes:category text=\"Politics\" />\n\n    </itunes:category>\n\n    <itunes:author>Politechs</itunes:author>\n    <link>https://politechs.dev</link>\n    <itunes:title>Politechs</itunes:title>\n    <itunes:type>Serial</itunes:type>\n    <copyright>All rights reserved, Politechs</copyright>\n\n    <item>\n      <title>Trailer</title>\n      <enclosure\n          url=\"https://politechs.dev/episodes/ai-00-trailer/ai-00-trailer.mp3\"\n          length=\"4492680\"\n          type=\"audio/mpeg\" />\n      <guid>https://politechs.dev/episodes/ai-00-trailer/ai-00-trailer.mp3</guid>\n      <pubDate>Mon, 7 Jul 2025 00:00:00 +0000</pubDate>\n      <description><![CDATA[<p>Since the invention of the wheel, automation has both substituted and complemented labour; machines replaced humans at some lower-paying jobs, but this was compensated by the creation of new, higher-paying jobs; in other words: tech workers. In recent years, there has been a desire from certain high-profile tech companies for an \"apolitical workplace\". This in itself is a political act; an act designed to suppress the power of tech workers and reinforce the status quo. They know that politics is inextricable from tech. And now so do you. Welcome to Politechs.</p><p>In Season 1 of Politechs, it's time to talk about AI: what it is, what it can and can't do, what makes it dangerous, and why we as tech workers must not buy into the inevitability narrative; why we must resist, and how we can resist.</p><p class=\"soundcljoud-hidden\">To view full show notes, including transcripts, please visit the<a href=\"https://politechs.devepisodes/ai-00-trailer/\">episode page</a>.</p><p>Cover art by <a href=\"https://anyakjordan.com/\">Anya K. Jordan</a><a href=\"https://bsky.app/profile/anyakjordan.bsky.social\">@anyakjordan.bsky.social</a></p><p>Theme music by <a href=\"https://soundcloud.com/ptzery\">Ptzery</a></p><p class=\"soundcljoud-hidden\">To view full show notes, including transcripts, please visit the<a href=\"https://politechs.devepisodes/ai-00-trailer/\">episode page</a>. Be sure to follow us on <a href=\"https://bsky.app/profile/politechs.bsky.social\">BlueSky</a> and <a href=\"https://mastodon.social/@politechs\">Mastodon</a>!</p><p>Cover art by <a href=\"https://anyakjordan.com/\">Anya K. Jordan</a><a href=\"https://bsky.app/profile/anyakjordan.bsky.social\">@anyakjordan.bsky.social</a></p><p>Theme music by <a href=\"https://soundcloud.com/ptzery\">Ptzery</a></p>]]></description>\n      <itunes:duration>251</itunes:duration>\n      <link>https://politechs.dev/episodes/ai-00-trailer</link>\n      <itunes:title>Trailer</itunes:title>\n      <itunes:episode>0</itunes:episode>\n      <itunes:episodeType>Trailer</itunes:episodeType>\n      <transcriptUrl>https://politechs.dev/episodes/ai-00-trailer/ai-00-trailer_transcription.txt</transcriptUrl>\n    </item>\n\n  </channel>\n</rss>\n"

  (def episode (-> opts :episodes first))

  episode
  ;; => {:description
  ;;     "\n<p>\n  Since the invention of the wheel, automation has both substituted and\n  complemented labour; machines replaced humans at some lower-paying jobs, but\n  this was compensated by the creation of new, higher-paying jobs; in other\n  words: tech workers. In recent years, there has been a desire from certain\n  high-profile tech companies for an \"apolitical workplace\". This in itself is\n  a political act; an act designed to suppress the power of tech workers and\n  reinforce the status quo. They know that politics is inextricable from tech.\n  And now so do you. Welcome to Politechs.\n</p>\n<p>\n  In Season 1 of Politechs, it's time to talk about AI: what it is, what it can\n  and can't do, what makes it dangerous, and why we as tech workers must not\n  buy into the inevitability narrative; why we must resist, and how we can\n  resist.\n</p>\n<p class=\"soundcljoud-hidden\">\n  To view full show notes, including transcripts, please visit the\n  <a href=\"{{base-url}}{{episode.path}}/\">episode page</a>.\n</p>\n<p>\n  Cover art by <a href=\"https://anyakjordan.com/\">Anya K. Jordan</a>\n  <a href=\"https://bsky.app/profile/anyakjordan.bsky.social\">@anyakjordan.bsky.social</a>\n</p>\n<p>\n  Theme music by <a href=\"https://soundcloud.com/ptzery\">Ptzery</a>\n</p>",
  ;;     :path "{{episodes-dir}}/{{slug}}",
  ;;     :transcript-file "{{slug}}_transcription.txt",
  ;;     :date "Mon, 7 Jul 2025 00:00:00 +0000",
  ;;     :slug "ai-{{number|number-format:%02d}}-trailer",
  ;;     :audio-file "{{slug}}.mp3",
  ;;     :number 0,
  ;;     :mime-type "audio/mpeg",
  ;;     :explicit false,
  ;;     :preview? true,
  ;;     :type "Trailer",
  ;;     :title "Trailer",
  ;;     :summary "{{podcast.description}}. Season 1: AI."}



  )
