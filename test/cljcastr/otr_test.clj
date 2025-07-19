(ns cljcastr.otr-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [cljcastr.transcription.otr :as otr]))

(deftest transcript-file-test

  (testing "happy path"
    (is (= "/a/b/foo/foo_transcription.otr"
           (-> (otr/transcript-file "/a/b/foo") str)))
    (is (= "/a/b/foo/foo_transcription.otr"
           (-> (otr/transcript-file "/a" "b" "foo") str))))

  )

(deftest transcript->paragraphs-test

  (testing "happy path"
    (is (= [{:ts "00:00.56"
             :speaker "Foo Bar"
             :text "Stuff and things"}
            {:ts "00:03.12"
             :speaker "Baz Blah"
             :text "Things and stuff"}]
           (->> {:text (str "<p>"
                        "<span class=\"timestamp\" data-timestamp=\"0.56\">00:00.56</span>"
                        "<b>Foo Bar</b>: Stuff and things"
                        "</p>\n"
                        "<p>"
                        "<span class=\"timestamp\" data-timestamp=\"3.12\">00:03.12</span>"
                        "<b>Baz Blah</b>: Things and stuff"
                        "</p>\n")}
                json/generate-string
                otr/transcript->paragraphs)))

    (is (= (->> {:text (str "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"0.56\">00:00.56</span>"
                            "<b>Foo Bar</b>: Stuff and things"
                            "</p>\n"
                            "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"3.12\">00:03.12</span>"
                            "<b>Baz Blah</b>: Things and stuff"
                            "</p>")}
                json/generate-string)
           (->> [{:ts "00:00.56"
                  :speaker "Foo Bar"
                  :text "Stuff and things"}
                 {:ts "00:03.12"
                  :speaker "Baz Blah"
                  :text "Things and stuff"}]
                otr/paragraphs->transcript)))

    )

  )
