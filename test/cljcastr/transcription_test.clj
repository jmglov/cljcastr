(ns cljcastr.transcription-test
  (:require [clojure.test :refer [deftest is testing]]
            [cljcastr.transcription :as transcription]))

(deftest remove-fillers-test

  (testing "no hours"
    (is (= [{:text "And so forth"}]
           (transcription/remove-fillers [{:text "Um, you know, and um so forth"}]))))

  )
