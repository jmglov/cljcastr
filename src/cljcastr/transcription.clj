(ns cljcastr.transcription
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(comment

  (def podcasts-root (fs/file (System/getenv "HOME") "Documents/projects"))

  (def podcast-name "politechs-pod")

  (def episode-name "ep-01-01-ai-overview")

  (def episode-dir (fs/file podcasts-root podcast-name episode-name))

  (def zencastr-transcript-file
    (fs/file episode-dir (format "%s-transcript.txt" episode-name)))

  (def zencastr-transcript
    (slurp zencastr-transcript-file))

  (->> zencastr-transcript
       str/split-lines
       (partition-by empty?)
       (remove (comp empty? first))
       (take 10))
  ;; => (("00:00:00.00" "Ray" "these fucking word forms.")
  ;;     ("00:00:00.06" "defn podcast" "from here")
  ;;     ("00:00:01.97" "Ray" "Alright.")
  ;;     ("00:00:02.10" "defn podcast" "Yeah, i think everything looks good.")
  ;;     ("00:00:02.87" "Ray" "Yeah,")
  ;;     ("00:00:06.59" "defn podcast" "Yours looks good.")
  ;;     ("00:00:09.07" "Ray" "yeah it looks good.")
  ;;     ("00:00:10.82"
  ;;      "defn podcast"
  ;;      "ah right. to alerts Should we just get fucking going in on this shit then? right. some Actually, let's see.")
  ;;     ("00:00:21.39" "defn podcast" "All right.")
  ;;     ("00:00:21.79"
  ;;      "Ray"
  ;;      "What's a bit annoying is that it's wiped out all the messages that you had up there."))
  ;; => (("00:00:00.00" "Ray" "these fucking word forms.")
  ;;     ("")
  ;;     ("00:00:00.06" "defn podcast" "from here")
  ;;     ("")
  ;;     ("00:00:01.97" "Ray" "Alright.")
  ;;     ("")
  ;;     ("00:00:02.10" "defn podcast" "Yeah, i think everything looks good.")
  ;;     ("")
  ;;     ("00:00:02.87" "Ray" "Yeah,")
  ;;     (""))
  ;; => (("00:00:00.00" "Ray" "these fucking word forms.")
  ;;     ("")
  ;;     ("00:00:00.06" "defn podcast" "from here")
  ;;     ("")
  ;;     ("00:00:01.97" "Ray"))
  ;; => ("00:00:00.00" "Ray" "these fucking word forms." "" "00:00:00.06")

  )
