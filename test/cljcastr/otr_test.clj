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
    (is (= [{:ts "00:00"
             :speaker nil
             :text "[Theme music starts]"
             :input-line 1}
            {:ts "00:02"
             :speaker nil
             :text "In other words: tech workers."
             :input-line 2}
            {:ts "00:46"
             :speaker "Josh"
             :text "Ray, I think we need to talk about trust."
             :input-line 3}
            {:ts "99:99:99.999"
             :speaker "Ray"
             :text "If we must, I suppose."
             :input-line 4}
            {:ts "00:50"
             :speaker "Josh"
             :text "So many spans!"
             :input-line 5}]
           (->> {:text (str "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"0\">00:00</span>"
                            "<b></b>"
                            "[Theme music starts]<br />"
                            "</p>"
                            "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"2.254929\">00:02</span>"
                            "In other words: tech workers."
                            "</p>"
                            "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"46.00\">00:46</span>"
                            "<b>Josh</b>: Ray, I think we need to talk about trust."
                            "</p>"
                            "<p>"
                            "<b>Ray</b>:     If we must, I suppose.   "
                            "</p>"
                            "<p>"
                            "<span>"
                            "<span class=\"timestamp\" data-timestamp=\"50.00\">00:50</span>"
                            "<b>Josh</b>"
                            "</span>"
                            "<span>: So many spans!</span>"
                            "</p>")}
                json/generate-string
                otr/transcript->paragraphs))))

  )

(deftest paragraphs->transcript-test

  (testing "Happy path"
    (is (= (->> {:text (str "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"0.56\">00:00</span>"
                            "<b>Foo Bar</b>: Stuff and things"
                            "</p>\n"
                            "<p>"
                            "<span class=\"timestamp\" data-timestamp=\"3.12\">00:03</span>"
                            "<b>Baz Blah</b>: Things and stuff"
                            "</p>")}
                json/generate-string)
           (->> [{:ts "00:00.56"
                  :speaker "Foo Bar"
                  :text "Stuff and things"}
                 {:ts "00:03.12"
                  :speaker "Baz Blah"
                  :text "Things and stuff"}]
                otr/paragraphs->transcript))))

  )
