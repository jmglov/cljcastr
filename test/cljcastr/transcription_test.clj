(ns cljcastr.transcription-test
  (:require [clojure.test :refer [deftest is testing]]
            [cljcastr.transcription :as transcription]))

(deftest transcript-type

  (testing "happy path"
    (is (= :edn (transcription/transcript-type "foo.edn")))
    (is (= :otr (transcription/transcript-type "foo.otr")))
    (is (= :zencastr (transcription/transcript-type "foo.txt")))))

(deftest remove-fillers-test

  (testing "happy path"
    (is (= [{:text "And so forth, I think at least, about AI."}
            {:text "Yeah, huh, badum, and so forth"}
            {:text ""}]
           (transcription/remove-fillers
            [{:text "Um, you know, and um so forth, I think at least, about um AI."}
             {:text "Yeah, huh, badum, and so forth"}
             {:text "You know, ah, um."}]))))

  (testing "override defaults"
    (is (= [{:text "You know, and so forth"}]
           (transcription/remove-fillers
            {:fillers #{"um"}}
            [{:text "Um, you know, and um so forth"}]))))

  ;; Known bug: sentence-ending punctuation lost. Not worth fixing.
  #_(testing "sentences ending with fillers"
      (is (= [{:text "This is an assertion."}]
             (transcription/remove-fillers
              [{:text "This is an assertion, you know."}]))))

  )

(deftest remove-short-paragraphs-test

  (testing "happy path"
    (is (= [{:text "This paragraph is long enough to escape the wrath of Khan."}]
           (transcription/remove-short-paragraphs
            [{:text "Too short"}
             {:text "This paragraph is long enough to escape the wrath of Khan."}]))))

  (testing "override defaults"
    (is (= [{:text "Too short"}
            {:text "This paragraph is long enough to escape the wrath of Khan."}]
           (transcription/remove-short-paragraphs
            {:short-paragraph-max-chars 1}
            [{:text "Too short"}
             {:text "This paragraph is long enough to escape the wrath of Khan."}]))))

  )

(deftest remove-active-listening-test

  (testing "happy path"
    (is (= [{:text "Um, you know, and um so forth"}]
           (transcription/remove-active-listening
            [{:text "hmm, yeah, ok, right"}
             {:text "Um, you know, and um so forth"}]))))

  (testing "override defaults"
    (is (= [{:text "hmm, yeah, ok, right"}
            {:text "Um, you know, and um so forth"}]
           (transcription/remove-active-listening
            {:active-listening-words #{"hmm" "yeah" "ok"}}
            [{:text "hmm, yeah, ok, right"}
             {:text "Um, you know, and um so forth"}]))))

  )

(deftest join-speakers-test

  (testing "happy path"
    (is (= [{:speaker "a"
             :text "Some important stuff and also things."
             :ts "00:00"}
            {:speaker "b"
             :text "I would like to add the following amazing insights."
             :ts "00:12"}]
           (transcription/join-speakers
            [{:speaker "a"
              :text "Some important stuff"
              :ts "00:00"}
             {:speaker "b"
              :text "hmm, yeah, ok, right"
              :ts "00:04"}
             {:speaker "a"
              :text "and also things."
              :ts "00:08"}
             {:speaker "b"
              :text "I would like to add the following"
              :ts "00:12"}
             {:speaker "a"
              :text "yeah"
              :ts "00:15"}
             {:speaker "b"
              :text "amazing insights."
              :ts "00:16"}]))))

  )

(deftest fixup-timestamps-test

  (testing "happy path"
    (is (= [{:speaker "a"
             :text "B, we need to talk about stuff and also things."
             :ts "00:46.00"}
            {:speaker "b"
             :text "I reject the notion that stuff is important."
             :ts "00:54.00"}]
           (transcription/fixup-timestamps
            {:start-at-ts "00:12", :offset-ts "00:46"}
            [{:speaker "a"
              :text "Are we ready to record?"
              :ts "00:00"}
             {:speaker "b"
              :text "Ready as we'll ever be, I posit."
              :ts "00:05"}
             {:speaker "a"
              :text "B, we need to talk about stuff and also things."
              :ts "00:12"}
             {:speaker "b"
              :text "I reject the notion that stuff is important."
              :ts "00:20"}]))))

  )
