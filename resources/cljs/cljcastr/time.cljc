(ns cljcastr.time
  (:require [clojure.string :as str]))

(defn parse-int [s]
  #?(:clj (Integer/parseInt s)
     :cljs (js/parseInt s)))

(defn parse-float [s]
  #?(:clj (Float/parseFloat s)
     :cljs (js/parseFloat s)))

(defn sec->ts
  ([sec]
   (sec->ts sec false))
  ([sec drop-decimal?]
   (let [hh (-> sec (/ 3600) int)
         mm (-> (- sec (* hh 3600)) (/ 60) int Math/abs)
         ss (+ (mod sec 60) 0.0)]
     #?(:clj
        (format "%s%s%02d:%05.2f"
                (if (neg? hh) "-" "")
                (if (zero? hh) "" (format "%02d:" (Math/abs hh)))
                mm
                ss)

        :cljs
        (let [hh-str (if (zero? hh) "" (str hh ":"))
              mm-str (-> (str mm) (str/replace #"^(\d)$" "0$1") (str ":"))
              ss-str (-> (str ss)
                         (str/replace #"^(\d+)$" "$1.00")
                         (str/replace #"^(\d)[.]" "0$1.")
                         (str/replace #"[.](\d{1,2})\d*" ".$1"))
              ss-str (if drop-decimal?
                       (str/replace ss-str #"[.]\d+$" "")
                       ss-str)]
          (str hh-str mm-str ss-str))))))

(defn ts->sec [ts]
  (when ts
    (let [ts (if (re-matches #"^-[0-9]{2}:[0-9]{2}(?:[.][0-9]+)?$" ts)
               #?(:clj
                  (format "-00:%s" (str/replace ts #"-" ""))

                  :cljs
                  (str "-00:" (str/replace ts #"-" "")))
               ts)
          ts (if (re-matches #"^[0-9]{2}:[0-9]{2}(?:[.][0-9]+)?$" ts)
               #?(:clj
                  (format "00:%s" ts)

                  :cljs
                  (str "00:" ts))
               ts)
          is-neg? (str/starts-with? ts "-")
          [hh mm ss frac-ss]
          (->> ts
               (re-matches #"(-?\d+):(\d+):(\d+)([.]\d+)?")
               (drop 1))
          [hh mm ss] (map parse-int [hh mm ss])
          frac-ss (parse-float (str "0" (or frac-ss ".0")))]
      (-> (Math/abs hh)
          (* 3600)
          (+ (* mm 60))
          (+ ss)
          (+ frac-ss)
          (* (if is-neg? -1 1))))))
