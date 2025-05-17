(ns cljcastr.transcription.zencastr
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [cljcastr.time :as time]
            [cljcastr.util :as util :refer [->map]]))

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
       (map (fn [[ts speaker text]] (->map ts speaker text)))))

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

(defn remove-short-paragraphs
  ([paragraphs]
   (remove-short-paragraphs paragraphs 20))
  ([paragraphs shorter-than]
   (->> paragraphs
        (remove (fn [{:keys [text]}] (< (count text) shorter-than))))))

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

(defn remove-active-listening
  ([paragraphs]
   (remove-active-listening paragraphs
                            #{"hmm"
                              "mm"
                              "ok"
                              "okay"
                              "right"
                              "sure"
                              "yeah"
                              "yep"
                              "yes"
                              "yup"}))
  ([paragraphs active-listing-words]
   (->> paragraphs
       (remove (fn [{:keys [text]}]
                 (->> text
                      (re-seq #"\w+")
                      (map str/lower-case)
                      (every? active-listing-words)))))))

(comment

  (->> paragraphs
       remove-active-listening
       (drop 10)
       (take 5))
  ;; => ({:ts "00:00:34.94", :speaker "Ray", :text "Yeah, yeah, yeah, yeah, for sure."}
  ;;     {:ts "00:00:36.99",
  ;;      :speaker "Ray",
  ;;      :text "um It's kind of like, but maybe what's interesting is like,"}
  ;;     {:ts "00:00:45.00",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "what was the last bit of new technology that felt like it was for our good that actually worked?"}
  ;;     {:ts "00:00:53.59",
  ;;      :speaker "Ray",
  ;;      :text "And it feels like it was probably a smartphone."}
  ;;     {:ts "00:00:56.64",
  ;;      :speaker "defn podcast",
  ;;      :text "Yeah, yeah. And even that, is it is it really on our side? but"})

  )

(defn join-speakers [paragraphs]
  (->> paragraphs
       remove-active-listening
       (partition-by :speaker)
       (map (fn [ps]
              (let [{:keys [ts speaker]} (first ps)
                    text (->> ps (map :text) (str/join " "))]
                (->map ts speaker text))))))

(comment

  (->> paragraphs
       remove-active-listening
       join-speakers
       (drop 10)
       (take 5))
  ;; => ({:ts "00:01:03.42",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "No, I know, but it feels like it's it's been a revolutionary thing that you know people leave home without ah people leave home with their smartphone more than their wallet. I mean, it is it has it has been a big thing. that people People, you know, I mean, I take photographs with it and organize my life with it."}
  ;;     {:ts "00:01:15.73",
  ;;      :speaker "defn podcast",
  ;;      :text "I do. yeah ah do Yeah, I do."}
  ;;     {:ts "00:01:21.32",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "I'm sure you do too, you know. So, you know, whether it's good or bad, it definitely we definitely use it a lot, you know, and we want to use it, you know."}
  ;;     {:ts "00:01:28.34", :speaker "defn podcast", :text "Yeah. and Yeah."}
  ;;     {:ts "00:01:32.38",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "yeah There's some bits and pieces that are, you know, less good, you know, but..."})

  )

(defn fixup-timestamps [{:keys [start-at]} paragraphs]
  (let [start-at-sec (time/ts->sec start-at)]
    (->> paragraphs
         (map (fn [{:keys [ts] :as p}]
                (assoc p :sec (- (time/ts->sec ts) start-at-sec))))
         (remove (comp neg? :sec))
         (map (fn [{:keys [sec] :as p}]
                (-> p
                    (assoc :ts (time/sec->ts sec))
                    (dissoc :sec)))))))

(comment

  (->> paragraphs
       remove-active-listening
       join-speakers
       (fixup-timestamps {:start-at "00:01:03.42"})
       (take 5))
  ;; => ({:ts "00:00:00.00",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "No, I know, but it feels like it's it's been a revolutionary thing that you know people leave home without ah people leave home with their smartphone more than their wallet. I mean, it is it has it has been a big thing. that people People, you know, I mean, I take photographs with it and organize my life with it."}
  ;;     {:ts "00:00:12.31",
  ;;      :speaker "defn podcast",
  ;;      :text "I do. yeah ah do Yeah, I do."}
  ;;     {:ts "00:00:17.90",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "I'm sure you do too, you know. So, you know, whether it's good or bad, it definitely we definitely use it a lot, you know, and we want to use it, you know."}
  ;;     {:ts "00:00:24.92", :speaker "defn podcast", :text "Yeah. and Yeah."}
  ;;     {:ts "00:00:28.96",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "yeah There's some bits and pieces that are, you know, less good, you know, but..."})

  )

(comment

  (def podcast-name "defn")
  (def episode-name "nathan-marz")
  (def episode-dir (fs/file podcasts-root podcast-name episode-name))
  (def transcript (->> (transcript-file episode-dir) slurp))
  (def paragraphs (->> transcript transcript->paragraphs))
  (def out-file (fs/file "/tmp" (format "%s_transcription.txt" episode-name)))

  (->> paragraphs
       remove-active-listening
       join-speakers
       (fixup-timestamps {:start-at "00:04:28.300"})
       (map (comp (partial str/join "\n") vals))
       (str/join "\n\n")
       (spit out-file))

  )
