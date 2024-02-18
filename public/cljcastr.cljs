;; To start a REPL:
;;
;; bb dev
;;
;; Then connect to it in Emacs:
;;
;; C-c l C (cider-connect-cljs), host: localhost; port: 1339; REPL type: nbb

(ns cljcastr
  (:require [clojure.string :as str]))

(def video-element (.querySelector js/document "video"))
(def video-select (.querySelector js/document "select#videoSource"))
(def audio-select (.querySelector js/document "select#audioSource"))
(def stop-button (.querySelector js/document "input#stop"))

(def active-stream (atom nil))

(defn log-error [e]
  (.error js/console e))

(defn log-devices
  ([devices]
   (log-devices nil devices))
  ([msg devices]
   (->> devices
        (group-by #(.-kind %))
        (sort-by key)
        (map (fn [[kind ds]]
               (str (or msg kind) "\n  "
                    (str/join "\n  " (map #(str (.-label %) " (" (.-deviceId %) ")") ds)))))
        (str/join "\n")
        println)
   devices))

(defn get-devices []
  (.enumerateDevices js/navigator.mediaDevices))

(defn audio-devices [devices]
  (filter #(= "audioinput" (.-kind %)) devices))

(defn video-devices [devices]
  (filter #(= "videoinput" (.-kind %)) devices))

(defn label-for [element]
  (->> (.-labels element) first .-textContent))

(defn populate-device-select! [select-element devices]
  (doseq [device (log-devices (str "Populating options for "
                                   (label-for select-element))
                              devices)]
    (let [option (.createElement js/document "option")]
      (set! (.-value option) (.-deviceId device))
      (set! (.-text option) (.-label device))
      (.appendChild select-element option))))

(defn populate-device-selects! [devices]
  (populate-device-select! audio-select (audio-devices devices))
  (populate-device-select! video-select (video-devices devices)))

(defn select-device! [select-element tracks]
  (let [label (-> tracks first (.-label))
        index (->> (.-options select-element)
                   (zipmap (range))
                   (some (fn [[i option]]
                           (and (= label (.-text option)) i))))]
    (when index
      (println "Setting index for" (label-for select-element) index)
      (set! (.-selectedIndex select-element) index))))

(defn start-media! [stream]
  (reset! active-stream stream)
  (select-device! audio-select (.getAudioTracks stream))
  (select-device! video-select (.getVideoTracks stream))
  (set! (.-srcObject video-element) stream))

(defn stop-media! []
  (when @active-stream
    (println "Stopping currently playing media")
    (doseq [track (.getTracks @active-stream)]
      (.stop track))))

(defn set-media-stream! []
  (stop-media!)
  (let [audio-source (.-value audio-select)
        video-source (.-value video-select)
        constraints {:audio {:deviceId (when (not-empty audio-source)
                                         {:exact audio-source})}
                     :video {:deviceId (when (not-empty video-source)
                                         {:exact video-source})}}]
    (println "Getting media with constraints:" constraints)
    (-> js/navigator.mediaDevices
        (.getUserMedia (clj->js constraints))
        (.then start-media!)
        (.catch log-error))))

(defn load-ui! []
  (set! (.-onchange audio-select) set-media-stream!)
  (set! (.-onchange video-select) set-media-stream!)
  (set! (.-onclick stop-button) stop-media!)
  (-> (set-media-stream!)
      (.then get-devices)
      (.then log-devices)
      (.then populate-device-selects!)))

(load-ui!)
