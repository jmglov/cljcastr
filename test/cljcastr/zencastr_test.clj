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
    (is (= [{:text "[Theme music starts]"}
            {:ts "00:02", :text "Since the invention of the wheel..."}
            {:text "[Theme music ends]"}
            {:ts "00:47",
             :speaker "Josh",
             :text "Ray, I think we should talk about some narratives and narrators."}
            {:ts "00:52", :speaker "Ray", :text "Yeah, I completely agree."}
            {:ts "02:13",
             :speaker "Josh",
             :text "Ants. They love to use the ant thing, right?"}
            {:speaker "Ray", :text "Totally!"}
            {:ts "1:23:45.678", :text "The end."}]
           (->> (str "[Theme music starts]\n"
                     "\n"
                     "00:02\n"
                     "Since the invention of the wheel...\n"
                     "\n"
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
                     "Ants. They love to use the ant thing, right?\n"
                     "\n"
                     "Ray\n"
                     "Totally!\n"
                     "\n"
                     "1:23:45.678\n"
                     "The end.")
                zencastr/transcript->paragraphs))))

  )

(deftest paragraphs->transcript-test

  (testing "Happy path"
    (is (= (str "[Theme music starts]\n"
                "\n"
                "00:02\n"
                "Since the invention of the wheel...\n"
                "\n"
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
                "Ants. They love to use the ant thing, right?")
           (->> [{:ts nil, :speaker nil, :text "[Theme music starts]"}
                 {:ts "00:02", :speaker nil, :text "Since the invention of the wheel..."}
                 {:ts nil, :speaker nil, :text "[Theme music ends]"}
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
