(ns cljcastr.time-test
  (:require [clojure.test :refer [deftest is testing]]
            [cljcastr.time :as time]))

(deftest sec->ts-test

  (testing "happy path"
    (is (= "03:25:45.32"
           (time/sec->ts 12345.321)))
    (is (= "-03:25:14.68"
           (time/sec->ts -12345.321))))

  (testing "no hours"
    (is (= "12:34.56"
           (time/sec->ts 754.56))))

  )

(deftest ts->sec-test

  (testing "happy path"
    (is (nil? (time/ts->sec nil)))
    (is (= 12345.319999992847
           (time/ts->sec "03:25:45.32")))
    (is (= -12345.319999992847
           (time/ts->sec "-03:25:45.32"))))

  (testing "no hours"
    (is (= "754.56"
           (->> (time/ts->sec "12:34.56")
                (format "%.2f")))))

  (testing "negative timestamp"
    (is (= -46.0
           (time/ts->sec "-00:46"))))

  )
