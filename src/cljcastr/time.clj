(ns cljcastr.time
  (:require [clojure.string :as str]))

(defn sec->ts [sec]
  (let [hh (-> sec (/ 3600) int)
        mm (-> (- sec (* hh 3600)) (/ 60) int Math/abs)
        ss (+ (mod sec 60) 0.0)]
    (format"%s%02d:%02d:%05.2f"
           (if (neg? hh) "-" "") (Math/abs hh) mm ss)))

(comment

  (sec->ts 12345.321)
  ;; => "03:25:45.32"

  (sec->ts -12345.321)
  ;; => "-03:25:14.68"

  )

(defn ts->sec [ts]
  (let [ts (if (re-matches #"^.+[.]\d+$" ts) ts (str ts ".0"))
        [hh mm ss frac-ss]
        (->> ts
             (re-matches #"(-?\d+):(\d+):(\d+)([.]\d+)")
             (drop 1))
        [hh mm ss] (map #(Integer/parseInt %) [hh mm ss])
        frac-ss (Float/parseFloat (str "0" frac-ss))]
    (-> (Math/abs hh)
        (* 3600)
        (+ (* mm 60))
        (+ ss)
        (+ frac-ss)
        (* (if (neg? hh) -1 1)))))

(comment

  (ts->sec "03:25:45.32")
  ;; => 12345.319999992847

  (ts->sec "-03:25:45.32")
  ;; => -12345.319999992847

  )
