(ns cljcastr.transcription
  (:require [babashka.fs :as fs]
            [cljcastr.time :as time]
            [cljcastr.transcription.edn :as edn]
            [cljcastr.transcription.otr :as otr]
            [cljcastr.transcription.zencastr :as zencastr]
            [cljcastr.util :as util :refer [->map]]
            [clojure.string :as str]))

(def defaults
  {:active-listening-words #{"hmm"
                             "mm"
                             "ok"
                             "okay"
                             "right"
                             "sure"
                             "yeah"
                             "yep"
                             "yes"
                             "yup"}

   :fillers #{"ah"
              "uh"
              "um"
              "you know"}

   :punctuation "[,.?!]"

   :short-paragraph-max-chars 20

   :offset-ts "00:00"
   :start-at-ts "00:00"})

(defn transcript-type [filename]
  (case (fs/extension filename)
    "edn" :edn
    "otr" :otr
    "txt" :zencastr))

(defn paragraph->words [{:keys [text] :as paragraph}]
  (->> (re-seq #"\S+" text)
       (map (fn [match]
              (let [[_ word punctuation] (re-matches #"^(.*\w)(\W+)$" match)]
                {:word (or word match), :punctuation punctuation})))))

(defn words->text [words]
  (->> words
       (reduce (fn [text {:keys [word punctuation]}]
                 (format "%s%s%s%s"
                         (if (empty? text) "" text)
                         (if (empty? text) "" " ")
                         word
                         (or punctuation "")))
               "")))

(defn remove-filler-word
  [filler-words
   {:keys [text sentence-start? cand-words filler-index] :as acc}
   word]
  (let [filler-word (nth filler-words filler-index)
        last-filler? (= filler-index (dec (count filler-words)))
        [_ matched] (re-matches (re-pattern (format "(?i)(%s)%s?"
                                                    filler-word
                                                    (:punctuation defaults)))
                                word)]
    (cond
      ;; Word matches current filler in phrase
      matched
      (assoc acc
             :cand-words (if last-filler?
                           []
                           (conj cand-words word))
             :filler-index (if last-filler? 0 (inc filler-index)))

      ;; Not a filler
      :else
      (assoc acc
             :text (if sentence-start?
                     (str/capitalize word)
                     (format "%s%s %s"
                             text
                             (if (empty? cand-words)
                               ""
                               (format " %s" (str/join " " cand-words)))
                             word))
             :sentence-start? false
             :cand-words []
             :filler-index 0))))

(defn remove-filler [acc filler]
  (let [{:keys [text]} acc]
    (let [filler-words (str/split filler #"\s")
          res (-> (partial remove-filler-word filler-words)
                  (reduce {:text text
                           :sentence-start? true
                           :filler-index 0
                           :cand-words []}
                          (str/split text #"\s")))]
      (if (re-matches
           (re-pattern (format "^(?i)%s%s?$"
                               filler
                               (:punctuation defaults)))
           (:text res))
        (assoc res :text "")
        res))))

(defn remove-paragraph-fillers [fillers text]
  (->> (reduce remove-filler {:text text} fillers)
       :text))

(defn remove-fillers
  ([paragraphs]
   (remove-fillers defaults paragraphs))
  ([{:keys [fillers] :or {fillers (:fillers defaults)} :as opts} paragraphs]
   (util/debug opts (format "Removing fillers: %s"
                            (str/join ", " fillers)))
   (->> paragraphs
        (map (fn [{:keys [text] :as paragraph}]
               (assoc paragraph :text
                      (remove-paragraph-fillers fillers text))))
        (remove (comp empty? :text)))))

(defn remove-repeated-words
  ([paragraphs]
   (remove-repeated-words defaults paragraphs))
  ([opts paragraphs]
   (util/debug opts "Removing repeated words")
   (->> paragraphs
        (map (fn [{:keys [text] :as paragraph}]
               (assoc paragraph :text
                      (->> paragraph
                           paragraph->words
                           (reduce (fn [acc {:keys [word punctuation] :as cur}]
                                     (if (and (not-empty acc)
                                              (= (str/lower-case word)
                                                 (str/lower-case (:word (last acc)))))
                                       acc
                                       (conj acc cur)))
                                   [])
                           words->text)))))))

(defn remove-short-paragraphs
  ([paragraphs]
   (remove-short-paragraphs defaults paragraphs))
  ([{:keys [short-paragraph-max-chars]
     :or {short-paragraph-max-chars (:short-paragraph-max-chars defaults)}
     :as opts}
    paragraphs]
   (util/debug opts
               (format "Removing paragraphs with fewer than %s characters"
                       short-paragraph-max-chars))
   (->> paragraphs
        (remove (fn [{:keys [text]}] (< (count text) short-paragraph-max-chars))))))

(defn remove-active-listening
  ([paragraphs]
   (remove-active-listening defaults paragraphs))
  ([{:keys [active-listening-words]
     :or {active-listening-words (:active-listening-words defaults)}
     :as opts}
    paragraphs]
   (util/debug opts
               (format "Removing active listening paragraphs; words: %s"
                       (str/join ", " active-listening-words)))
   (->> paragraphs
        (remove (fn [{:keys [text]}]
                  (->> text
                       (re-seq #"\w+")
                       (map str/lower-case)
                       (every? active-listening-words)))))))

(defn join-speakers
  ([paragraphs]
   (join-speakers defaults paragraphs))
  ([opts paragraphs]
   (util/debug opts "Joining speakers")
   (->> paragraphs
        remove-active-listening
        (partition-by :speaker)
        (map (fn [ps]
               (let [{:keys [ts speaker]} (first ps)
                     text (->> ps (map :text) (str/join " "))]
                 (->map ts speaker text)))))))

(defn fixup-timestamps
  ([paragraphs]
   (fixup-timestamps defaults paragraphs))
  ([{:keys [start-at-ts offset-ts]
     :or {start-at-ts (:start-at-ts defaults)
          offset-ts (:offset-ts defaults)}
     :as opts}
    paragraphs]
   (let [start-at-sec (or (time/ts->sec start-at-ts) 0)
         offset-sec (or (time/ts->sec offset-ts) 0)]
     (util/debug opts
                 (format "Fixup timestamps: starting at %s, offsetting %s seconds"
                         start-at-ts offset-sec))
     (->> paragraphs
          (map (fn [{:keys [ts] :as p}]
                 (if ts
                   (assoc p :sec (- (time/ts->sec ts) start-at-sec))
                   p)))
          (remove (fn [{:keys [sec]}]
                    (and sec (neg? sec))))
          (map (fn [{:keys [sec] :as p}]
                 (if sec
                   (-> p
                       (assoc :ts (time/sec->ts (+ sec offset-sec)))
                       (dissoc :sec))
                   p)))))))

(defn remove-timestamps
  ([paragraphs]
   (remove-timestamps defaults paragraphs))
  ([opts paragraphs]
   (util/debug opts "Removing timestamps")
   (->> paragraphs
        (map #(dissoc % :ts)))))

(defn parse-fn [filename]
  (case (transcript-type filename)
    :edn edn/transcript->paragraphs
    :otr otr/transcript->paragraphs
    :zencastr zencastr/transcript->paragraphs))

(defn generate-fn [filename]
  (case (transcript-type filename)
    :edn edn/paragraphs->transcript
    :otr otr/paragraphs->transcript
    :zencastr zencastr/paragraphs->transcript))
