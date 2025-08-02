(ns cljcastr.zencastr-test
  (:require [clojure.test :refer [deftest is testing]]
            [cljcastr.transcription.zencastr :as zencastr]))

(deftest transcript-file-test

  (testing "happy path"
    (is (= "/a/b/foo/foo_transcription.txt"
           (-> (zencastr/transcript-file "/a/b/foo") str)))
    (is (= "/a/b/foo/foo_transcription.txt"
           (-> (zencastr/transcript-file "/a" "b" "foo") str))))

  )

(deftest transcript->paragraphs-test

  (testing "happy path"
    (is (= [{:ts "00:00", :speaker nil, :text "[Theme music starts]"}
            {:ts "00:02", :speaker nil, :text "Since the invention of the wheel..."}
            {:ts "00:44", :speaker nil, :text "[Theme music ends]"}
            {:ts "00:47",
             :speaker "Josh",
             :text "Ray, I think we should talk about some narratives and narrators."}
            {:ts "00:52", :speaker "Ray", :text "Yeah, I completely agree."}
            {:ts "02:13",
             :speaker "Josh",
             :text "Ants. They love to use the ant thing, right?"}]
           (->> (str "00:00\n"
                     "[Theme music starts]\n"
                     "\n"
                     "00:02\n"
                     "Since the invention of the wheel...\n"
                     "\n"
                     "00:44\n"
                     "[Theme music ends]\n"
                     "\n"
                     "00:47\n"
                     "Josh\n"
                     "Ray, I think we should talk about some narratives and narrators.\n"
                     "\n"
                     "00:52\n"
                     "Ray\n"
                     "Yeah, I completely agree.\n"
                     "\n"
                     "02:13\n"
                     "Josh\n"
                     "Ants. They love to use the ant thing, right?")
                zencastr/transcript->paragraphs))))

  )

(deftest paragraphs->transcript-test

  (testing "Happy path"
    (is (= (->> (str "00:00\n"
                     "[Theme music starts]\n"
                     "\n"
                     "00:02\n"
                     "Since the invention of the wheel...\n"
                     "\n"
                     "00:44\n"
                     "[Theme music ends]\n"
                     "\n"
                     "00:47\n"
                     "Josh\n"
                     "Ray, I think we should talk about some narratives and narrators.\n"
                     "\n"
                     "00:52\n"
                     "Ray\n"
                     "Yeah, I completely agree.\n"
                     "\n"
                     "Josh\n"
                     "Um\n"
                     "\n"
                     "02:13\n"
                     "Josh\n"
                     "Ants. They love to use the ant thing, right?"))
           (->> [{:ts "00:00", :speaker nil, :text "[Theme music starts]"}
                 {:ts "00:02", :speaker nil, :text "Since the invention of the wheel..."}
                 {:ts "00:44", :speaker nil, :text "[Theme music ends]"}
                 {:ts "00:47",
                  :speaker "Josh",
                  :text "Ray, I think we should talk about some narratives and narrators."}
                 {:ts "00:52", :speaker "Ray", :text "Yeah, I completely agree."}
                 {:speaker "Josh" :text "Um"}
                 {:ts "02:13",
                  :speaker "Josh",
                  :text "Ants. They love to use the ant thing, right?"}]
                zencastr/paragraphs->transcript))))

  )
