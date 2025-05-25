(ns cljcastr.transcription.zencastr
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [cljcastr.util :refer [->map]]))

(comment

  (def podcasts-root (fs/file (System/getenv "HOME") "Documents/projects"))
  ;; => #'cljcastr.transcription.zencastr/podcasts-root

  (def podcast-name "politechs-pod")
  ;; => #'cljcastr.transcription.zencastr/podcast-name

  (def episode-name "ep-01-01-ai-overview")
  ;; => #'cljcastr.transcription.zencastr/episode-name

  (def episode-dir (fs/file podcasts-root podcast-name episode-name))
  ;; => #'cljcastr.transcription.zencastr/episode-dir

  )

(defn transcript-file
  ([podcasts-root podcast-name episode-name]
   (transcript-file (fs/file podcasts-root podcast-name episode-name)))
  ([episode-dir]
   (let [episode-name (fs/file-name episode-dir)]
     (fs/file episode-dir (format "%s_transcription.txt" episode-name)))))

(comment

  (transcript-file podcasts-root podcast-name episode-name)
  ;; => #object[java.io.File 0x20408a2a "/home/jmglov/Documents/projects/politechs-pod/ep-01-01-ai-overview/ep-01-01-ai-overview_transcription.txt"]

  (transcript-file episode-dir)
  ;; => #object[java.io.File 0x625e4a76 "/home/jmglov/Documents/projects/politechs-pod/ep-01-01-ai-overview/ep-01-01-ai-overview_transcription.txt"]

  )

(defn transcript->paragraphs [transcript]
  (->> transcript
       str/split-lines
       (partition-by empty?)
       (remove (comp empty? first))
       (map (fn [[ts speaker text]]
              (let [[speaker text] (if text [speaker text] ["" speaker])]
                (->map ts speaker text))))))

(comment

  (def transcript (->> (transcript-file episode-dir) slurp))
  ;; => #'cljcastr.transcription.zencastr/transcript

  (->> transcript
       transcript->paragraphs
       (drop 15)
       (take 5))
  ;; => ({:ts "00:00:51.72", :speaker "defn podcast", :text "Right."}
  ;;     {:ts "00:00:53.59",
  ;;      :speaker "Ray",
  ;;      :text "And it feels like it was probably a smartphone."}
  ;;     {:ts "00:00:53.57", :speaker "defn podcast", :text "Right."}
  ;;     {:ts "00:00:56.20", :speaker "Ray", :text "Yeah."}
  ;;     {:ts "00:00:56.64",
  ;;      :speaker "defn podcast",
  ;;      :text "Yeah, yeah. And even that, is it is it really on our side? but"})

  )

(comment

  (def paragraphs (->> transcript transcript->paragraphs))
  ;; => #'cljcastr.transcription.zencastr/paragraphs

  (->> paragraphs
       remove-short-paragraphs
       (drop 15)
       (take 5))
  ;; => ({:ts "00:01:32.38",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "yeah There's some bits and pieces that are, you know, less good, you know, but..."}
  ;;     {:ts "00:01:35.97", :speaker "defn podcast", :text "yeah yeah yeah Yeah."}
  ;;     {:ts "00:01:40.13",
  ;;      :speaker "defn podcast",
  ;;      :text
  ;;      "Well, let's, let's, let's fucking go say it on the air. I mean, like it was but we can definitely do the thing where, you know, if you feel like you're developing a thought out loud and then you kind of get it and you want to restate it, um,"}
  ;;     {:ts "00:01:55.53",
  ;;      :speaker "defn podcast",
  ;;      :text
  ;;      "We actually need a safe word that I can search for in the transcript. ah Should we just say retake? Because that's a word we're unlikely going to say in the normal."}
  ;;     {:ts "00:02:04.20", :speaker "Ray", :text "retail yeah Yeah, that's true."})

  )

(defn paragraphs->transcript [paragraphs]
  (->> paragraphs
       (map (fn [{:keys [ts speaker text]}]
              (->> (if (empty? speaker)
                     [ts text "\n"]
                     [ts speaker text "\n"])
                   (str/join "\n"))))
       str/join
       str/trimr))

(comment

  (->> paragraphs
       (take 5)
       (paragraphs->transcript))
  ;; => "00:00:00.00\n[Theme music begins]\n\n00:00:02.00\nJosh\nSince the invention of the wheel, automation has both substituted and complemented labour; machines replaced humans at some lower-paying jobs, but this was compensated by the creation of new, higher-paying jobs; in other words: tech workers. In recent years, there has been a desire from certain high-profile tech companies for an \"apolitical workplace\". This in itself is a political act; an act designed to suppress the power of tech workers and reinforce the status quo. They know that politics is inextricable from tech. And now so do you. Welcome to Politechs.\n\n00:00:45.00\nRay\nHey, hi, Josh.\n\n00:00:47.00\ndefn podcast\nHey, Ray.\n\n00:00:48.00\nRay\nI've had a bit of a way of thinking about expressing AI. AI is everywhere and nowhere. What do you think?"

  (spit (fs/file episode-dir "transcript.txt")
        (paragraphs->transcript paragraphs))
  ;; => nil

  )
