(ns cljcastr.edn-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [cljcastr.transcription.edn :as edn]))

(deftest transcript-file-test

  (testing "happy path"
    (is (= "/a/b/foo/foo_transcription.edn"
           (-> (edn/transcript-file "/a/b/foo") str)))
    (is (= "/a/b/foo/foo_transcription.edn"
           (-> (edn/transcript-file "/a" "b" "foo") str))))

  )

(deftest transcript->paragraphs-test

  (testing "happy path"
    (is (= [{:speaker "a"
             :text "B, we need to talk about stuff and also things."
             :ts "00:46.00"}
            {:speaker "b"
             :text "I reject the notion that stuff is important."
             :ts "00:54.00"}]
           (edn/transcript->paragraphs
            (str "{:transcript\n"
                 " [{:speaker \"a\",\n"
                 "   :text \"B, we need to talk about stuff and also things.\",\n"
                 "   :ts \"00:46.00\"}\n"
                 "  {:speaker \"b\",\n"
                 "   :text \"I reject the notion that stuff is important.\",\n"
                 "   :ts \"00:54.00\"}]}\n")))))

  )

(deftest paragraphs->transcript-test

  (testing "happy path"
    (is (= (str "{:transcript\n"
                " [{:speaker \"a\",\n"
                "   :text \"B, we need to talk about stuff and also things.\",\n"
                "   :ts \"00:46.00\"}\n"
                "  {:speaker \"b\",\n"
                "   :text \"I reject the notion that stuff is important.\",\n"
                "   :ts \"00:54.00\"}]}\n")
           (edn/paragraphs->transcript
            [{:speaker "a"
             :text "B, we need to talk about stuff and also things."
             :ts "00:46.00"}
            {:speaker "b"
             :text "I reject the notion that stuff is important."
             :ts "00:54.00"}]))))

  )
