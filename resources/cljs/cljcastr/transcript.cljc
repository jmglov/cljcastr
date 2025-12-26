(ns cljcastr.transcript
  (:require [cljcastr.transcription.zencastr :as zencastr]
            [clojure.edn :as edn]))

(defn parse-transcript [transcript-type text]
  (case transcript-type
    :edn
    (-> (edn/read-string text)
        :transcript)

    :json
    (-> (js/JSON.parse text) (.-text))

    :txt
    (zencastr/transcript->paragraphs text)))
