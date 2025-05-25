(ns cljcastr.transcription
  (:require [babashka.fs :as fs]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [cljcastr.time :as time]
            [cljcastr.util :as util :refer [->map]]))

(defn remove-short-paragraphs
  ([paragraphs]
   (remove-short-paragraphs paragraphs 20))
  ([paragraphs shorter-than]
   (->> paragraphs
        (remove (fn [{:keys [text]}] (< (count text) shorter-than))))))

(comment

  (def podcasts-root (fs/file (System/getenv "HOME") "Documents/projects"))
  ;; => #'cljcastr.transcription/podcasts-root

  (def podcast-name "politechs-pod")
  ;; => #'cljcastr.transcription/podcast-name

  (def episode-name "ai-01-ai")
  ;; => #'cljcastr.transcription/episode-name

  (def episode-dir (fs/file podcasts-root podcast-name episode-name))
  ;; => #'cljcastr.transcription/episode-dir

  (require '[cljcastr.transcription.zencastr :as z])
  ;; => nil

  (def transcript (slurp (z/transcript-file episode-dir)))
  ;; => #'cljcastr.transcription/transcript

  (def paragraphs (->> transcript z/transcript->paragraphs))

  (->> paragraphs
       remove-short-paragraphs
       (drop 15)
       (take 5))
  ;; => ({:ts "00:02:28.79",
  ;;      :speaker "Ray",
  ;;      :text "Maybe this maybe is just my earphones."}
  ;;     {:ts "00:02:31.96",
  ;;      :speaker "defn podcast",
  ;;      :text "Yeah. You got to turn up your, turn up your earphones mate."}
  ;;     {:ts "00:02:36.25",
  ;;      :speaker "Ray",
  ;;      :text "ah where What are you saying there, Sonny?"}
  ;;     {:ts "00:02:39.09",
  ;;      :speaker "defn podcast",
  ;;      :text
  ;;      "Dude, my waveform is looking tight as fuck. So, you know, yeah, that's right."}
  ;;     {:ts "00:02:41.87",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "Yeah, it is looking good. It is looking good. Yeah. It's not quite as good as mine, but it's pretty good."})

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
  ;; => ({:ts "00:01:34.91",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "It's like they're flooding the zone in terms of, you know, having AI everywhere."}
  ;;     {:ts "00:01:41.05",
  ;;      :speaker "Ray",
  ;;      :text "um And it just then becomes a natural, normal part of existence."}
  ;;     {:ts "00:01:47.28",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "Even though you know nobody asked for it, nobody particularly thinks it's any good, um but it now becomes like a cost of doing business."}
  ;;     {:ts "00:01:57.07", :speaker "defn podcast", :text "Yeah, definitely."}
  ;;     {:ts "00:01:57.74",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "and you know Even if there are sort of good things, it's it I want to say something about the fact that you know the ah cost of this thing is so huge."})

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
  ;; => ({:ts "00:02:39.09",
  ;;      :speaker "defn podcast",
  ;;      :text
  ;;      "Dude, my waveform is looking tight as fuck. So, you know, yeah, that's right."}
  ;;     {:ts "00:02:41.87",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "Yeah, it is looking good. It is looking good. Yeah. It's not quite as good as mine, but it's pretty good."}
  ;;     {:ts "00:02:45.02",
  ;;      :speaker "defn podcast",
  ;;      :text
  ;;      "um yeah Well, the year you're blowing out the high end. You're dissing all over the place."}
  ;;     {:ts "00:02:51.53", :speaker "Ray", :text "No, I'm not."}
  ;;     {:ts "00:02:52.37",
  ;;      :speaker "defn podcast",
  ;;      :text "yeah you got it Yeah, you got to turn down your levels, mate."})

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
       (fixup-timestamps {:start-at "00:03:39.21"})
       remove-active-listening
       join-speakers
       (take 5))
  ;; => ({:ts "00:00:00.18",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "Yeah, yeah exactly exactly. So it's kind of like, okay, let's let's you know break down what this thing is, explain it in a bit more detail, explain why, you know, that it's not magical, that it has got limitations, that sure, sure, there are some potential uses for some of this stuff in very, very niche use cases. um But is it worth burning the planet for it? Is it worth spending, you know, is it worth like having a war about it?"}
  ;;     {:ts "00:00:36.42", :speaker "defn podcast", :text "Exactly."}
  ;;     {:ts "00:00:39.05",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "Because this is, i mean, literally what we're talking about now, you know, this, this JD Vance coming over to Europe, telling, telling us that,"}
  ;;     {:ts "00:00:46.14", :speaker "defn podcast", :text "Oh, yeah."}
  ;;     {:ts "00:00:49.27",
  ;;      :speaker "Ray",
  ;;      :text
  ;;      "they're now in charge of AI. They're going to have the best AI and they're open for business. We are going to be client states. I mean, the colonialism is right on display. i mean I don't think you can spell colonialism without AI. Boom."})

  )

(defn paragraphs->edn [paragraphs]
  (with-out-str
    (->> paragraphs
         vec
         (assoc {} :transcript)
         pprint)))

(comment

  (->> paragraphs
       (take 5)
       paragraphs->edn)
  ;; => "{:transcript\n [{:ts \"00:00:00.32\", :speaker \"defn podcast\", :text \"dive right in\"}\n  {:ts \"00:00:01.55\",\n   :speaker \"Nathan\",\n   :text \"Mm-hmm. Mm-hmm. Mm-hmm.\"}\n  {:ts \"00:00:03.96\", :speaker \"Ray\", :text \"Okay.\"}\n  {:ts \"00:00:04.83\",\n   :speaker \"defn podcast\",\n   :text\n   \"Everybody good on water and biological functions, etc. Okay. All right.\"}\n  {:ts \"00:00:11.01\", :speaker \"Ray\", :text \"Yep.\"}]}\n"

  )

(comment

  (def podcast-name "defn")
  (def episode-name "nathan-marz")
  (def episode-dir (fs/file podcasts-root podcast-name episode-name))
  (def transcript (->> (z/transcript-file episode-dir) slurp))
  (def paragraphs (->> transcript z/transcript->paragraphs))
  (def out-file (fs/file "/tmp" (format "%s_transcription.txt" episode-name)))

  (->> paragraphs
       (fixup-timestamps {:start-at "00:03:39.21"})
       remove-active-listening
       join-speakers
       z/paragraphs->transcript
       (spit out-file))

  (def out-edn (fs/file "/tmp" (format "%s_transcription.edn" episode-name)))

  (->> paragraphs
       (fixup-timestamps {:start-at "00:03:39.21"})
       remove-active-listening
       join-speakers
       paragraphs->edn
       (spit out-edn))

  )
