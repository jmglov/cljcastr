(ns cljcastr.time
  (:require [clojure.string :as str]))

(defn sec->ts [sec]
  (let [hh (-> sec (/ 3600) int)
        mm (-> (- sec (* hh 3600)) (/ 60) int Math/abs)
        ss (+ (mod sec 60) 0.0)]
    (format"%s%s%02d:%05.2f"
           (if (neg? hh) "-" "")
           (if (zero? hh) "" (format "%02d:" (Math/abs hh)))
           mm
           ss)))

(defn ts->sec [ts]
  (when ts
    (let [ts (if (re-matches #"^-[0-9]{2}:[0-9]{2}(?:[.][0-9]+)?$" ts)
               (format "-00:%s" (str/replace ts #"-" ""))
               ts)
          ts (if (re-matches #"^[0-9]{2}:[0-9]{2}(?:[.][0-9]+)?$" ts)
               (format "00:%s" ts)
               ts)
          is-neg? (str/starts-with? ts "-")
          [hh mm ss frac-ss]
          (->> ts
               (re-matches #"(-?\d+):(\d+):(\d+)([.]\d+)?")
               (drop 1))
          [hh mm ss] (map #(Integer/parseInt %) [hh mm ss])
          frac-ss (Float/parseFloat (str "0" (or frac-ss ".0")))]
      (-> (Math/abs hh)
          (* 3600)
          (+ (* mm 60))
          (+ ss)
          (+ frac-ss)
          (* (if is-neg? -1 1))))))
