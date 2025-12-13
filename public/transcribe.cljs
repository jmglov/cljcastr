;; To start a REPL:
;;
;; bb dev
;;
;; Then connect to it in Emacs:
;;
;; C-c l C (cider-connect-cljs), host: localhost; port: 1339; REPL type: nbb

(ns cljcastr.transcript
  (:require [cljcastr.dom :as dom]
            [cljcastr.time :as time]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]))

(defonce state (atom {}))

(def seek-duration-sec 1.0)

(def speed-step 0.25)

(def log-level (keyword (.-CLJCASTR_LOG_LEVEL js/window)))

(def log-level->int {:error 0
                     :warn 1
                     :info 2
                     :debug 3})

(defn log [level & args]
  (when (<= (log-level->int level) (log-level->int log-level))
    (apply js/console.log args))
  (last args))

(defn hide-message! []
  (dom/set-styles! "#message" "display: none"))

(defn show-message!
  ([msg]
   (show-message! :info msg))
  ([msg-type msg]
   (log msg-type msg)
   (let [message-el (dom/get-el "#message")
         text-el (dom/get-el "#message-text")]
     (dom/set-class! message-el (name msg-type))
     (dom/set-text! text-el msg)
     (dom/set-styles! message-el "display: flex"))))

(defn error! [msg]
  (show-message! :error msg))

(defn hide-message! []
  (-> (dom/get-el "#message") (dom/set-styles! "display: none")))

(defn get-audio-duration []
  (-> (dom/get-el "audio") .-duration))

(defn get-audio-ts []
  (-> (dom/get-el "audio") .-currentTime))

(defn set-audio-ts! [ts]
  (set! (.-currentTime (dom/get-el "audio")) ts))

(defn get-audio-playback-rate []
  (-> (dom/get-el "audio") .-playbackRate))

(defn set-audio-playback-rate! [rate]
  (set! (.-playbackRate (dom/get-el "audio")) rate))

(defn clear-storage! []
  (let [storage (.-localStorage js/window)]
    (log :debug "Clearing" (.-length storage) "keys from local storage")
    (.clear storage)))

(defn read-transcript [text]
  (-> (edn/read-string text)
      :transcript))

(defn create-transcript-span [i [k v]]
  (when v
    (let [el (dom/create-el
              "span"
              {:id (str "transcript-p-" i "-" (name k))
               :class (str "transcript-" (name k))})]
      (set! (.-innerText el) v)
      el)))

(defn create-transcript-spans [i paragraph]
  (->> [:ts :speaker :text]
       (map (fn [k] (create-transcript-span i [k (get paragraph k)])))
       (remove nil?)))

(defn create-transcript-p [i paragraph]
  (let [p (dom/create-el "p" {:id (str "transcript-p-" i)})]
    (dom/set-children! p (create-transcript-spans i paragraph))
    p))

(defn transcript->elements [transcript]
  (map-indexed create-transcript-p transcript))

(defn load-key [k]
  (-> (.-localStorage js/window)
      (.getItem k)))

(defn save-key! [k v]
  (log :debug "Saving key" k "to local storage:" v)
  (-> (.-localStorage js/window)
      (.setItem k v)))

(defn load-num-paragraphs []
  (-> (load-key "transcript-num-paragraphs")
      js/parseInt))

(defn save-num-paragraphs! [n]
  (save-key! "transcript-num-paragraphs" (str n)))

(defn inc-num-paragraphs! []
  (let [n (load-num-paragraphs)]
    (save-num-paragraphs! (inc n))))

(defn save-paragraph! [p-el]
  (doseq [span-el (.-childNodes p-el)
          :let [k (.-id span-el)
                v (.-textContent span-el)]]
    (save-key! k v)))

(defn load-transcript-filename []
  (load-key "transcript-filename"))

(defn save-transcript-filename! [filename]
  (swap! state assoc :transcript-filename filename)
  (save-key! "transcript-filename" filename))

(defn load-transcript-url []
  (load-key "transcript-url"))

(defn save-transcript-url! [url]
  (swap! state assoc :transcript-url url)
  (save-key! "transcript-url" url))

(defn load-audio-filename []
  (load-key "audio-filename"))

(defn save-audio-filename! [filename]
  (swap! state assoc :audio-filename filename)
  (save-key! "audio-filename" filename))

(defn load-audio-url [url]
  (load-key "audio-url" url))

(defn save-audio-url! [url]
  (swap! state assoc :audio-url url)
  (save-key! "audio-url" url))

(defn load-paragraph [i]
  (log :debug "Loading paragraph" i "from local storage")
  {:ts (load-key (str "transcript-p-" i "-ts"))
   :speaker (load-key (str "transcript-p-" i "-speaker"))
   :text (load-key (str "transcript-p-" i "-text"))})

(defn load-transcript []
  (let [num-paragraphs (load-num-paragraphs)]
    (when (pos-int? num-paragraphs)
      (log :info "Loading transcript from local storage; restoring"
           num-paragraphs "paragraphs")
      (->> (range num-paragraphs)
           (map load-paragraph)))))

(defn clear-transcript! [target-el]
  (dom/clear-children! target-el))

(defn display-transcript! [target-el transcript]
  (doseq [p (transcript->elements transcript)]
    (.appendChild target-el p)))

(defn display-audio-duration! []
  (dom/set-text! (dom/get-el "#audio-dur")
                 (time/sec->ts (get-audio-duration) true)))

(defn display-audio-ts! []
  (dom/set-text! (dom/get-el "#audio-ts")
                 (time/sec->ts (get-audio-ts) true)))

(defn display-audio-playback-rate! []
  (dom/set-text! (dom/get-el "#audio-rate")
                 (str (get-audio-playback-rate) "x")))

(defn display-audio! []
  (display-audio-ts!)
  (display-audio-duration!)
  (display-audio-playback-rate!)
  (dom/set-styles! (dom/get-el "#audio-controls") "display: inline"))

(defn restore-transcript! [target-el]
  (display-transcript! target-el (load-transcript)))

(defn save-edn! [filename data]
  (let [a (dom/create-el "a")
        blob (js/Blob. [(pr-str data)] (clj->js {:type "application/edn"}))]
    (set! (.-href a) (js/URL.createObjectURL blob))
    (set! (.-download a) filename)
    (.click a)))

(defn save-transcript! [target-el]
  (let [children (.-childNodes target-el)]
    (doseq [p children]
      (save-paragraph! p))))

(defn export-transcript! []
  (save-edn! (or (load-transcript-filename) "transcript.edn")
             {:transcript (vec (load-transcript))}))

(defn import-transcript! [target-el transcript]
  (swap! state assoc :transcript transcript)
  (clear-transcript! target-el)
  (display-transcript! target-el transcript)
  (clear-storage!)
  (save-transcript! target-el)
  (save-num-paragraphs! (count transcript)))

(defn import-transcript-file! [target-el filename event]
  (let [contents (-> event .-target .-result)
        transcript (read-transcript contents)]
    (log :debug "Loaded file:" contents)
    (import-transcript! target-el transcript)
    (save-transcript-filename! filename)))

(defn import-transcript-url! [target-el url]
  (log :debug "Fetching transcript from:" url)
  (p/let [response (js/fetch (js/Request. url))]
    (log :debug "Fetch transcript response:" response)
    (if (.-ok response)
      (do
        (p/->> response
               .text
               read-transcript
               (import-transcript! target-el))
        (save-transcript-url! url))
      (error! (str "Failed to import transcript from URL " url ": "
                   (.-statusText response))))))

(defn read-transcript-file! [target-el event]
  (log :debug "Transcript file selected:" event)
  (let [file (-> event .-target .-files first)
        reader (js/FileReader.)]
    (set! (.-onload reader)
          (partial import-transcript-file! target-el (.-name file)))
    (.readAsText reader file)))

(defn load-audio! [audio-el event]
  (log :debug "Audio file selected:" event)
  (let [file (-> event .-target .-files first)
        url (js/URL.createObjectURL file)]
    (set! (.-src audio-el) url)
    (save-audio-filename! (.-name file))
    (display-audio!)
    (swap! state assoc :paused true)))

(defn open-audio-url! [audio-el url]
  (log :debug "Loading audio from URL:" url)
  (set! (.-src audio-el) url)
  (save-audio-url! url))

(defn audio-loaded? []
  (or (:audio-filename @state) (:audio-url @state)))

(defn handle-audio-error! [ev]
  (let [err (-> ev .-target .-error)
        src (or (:audio-filename @state) (:audio-url @state))
        msg (cond
              (= (.-code err) (.-MEDIA_ERR_ABORTED err))
              "Audio playback aborted by user"

              (= (.-code err) (.-MEDIA_ERR_NETWORK err))
              (str "Audio download failed from URL: " src)

              (= (.-code err) (.-MEDIA_ERR_DECODE err))
              (str "Failed to decode audio: " src)

              (= (.-code err) (.-MEDIA_ERR_SRC_NOT_SUPPORTED err))
              (if (audio-loaded?)
                (str "Audio failed to load from "
                     (if (:audio-filename @state)
                       (str "file: " src "; format not supported")
                       (str "URL: " src "; file not found or format not supported"))))

              :else
              "Unknown audio error")]
    (error! msg)))

(defn play-pause! []
  (when (audio-loaded?)
    (let [audio (dom/get-el "audio")
          pos (-> (get-audio-ts) (time/sec->ts false))]
      (if (:paused @state)
        (do
          (log :debug "Playing audio from" pos)
          (.play audio)
          (swap! state assoc :paused false))
        (do
          (log :debug "Pausing audio at" pos)
          (.pause audio)
          (swap! state assoc :paused true))))))

(defn seek! [delta]
  (when (audio-loaded?)
    (let [ts (get-audio-ts)
          target-ts (+ ts delta)]
      (log :debug
           "Seeking from" (time/sec->ts ts false)
           "to" (time/sec->ts target-ts false))
      (set-audio-ts! target-ts))))

(defn seek-backward! []
  (seek! (* seek-duration-sec (get-audio-playback-rate) -1)))

(defn seek-forward! []
  (seek! (* seek-duration-sec (get-audio-playback-rate))))

(defn set-speed! [delta]
  (when (audio-loaded?)
    (let [rate (get-audio-playback-rate)
          target-rate (+ rate delta)]
      (log :debug
           "Changing speed from" rate "to" target-rate)
      (set-audio-playback-rate! target-rate))))

(defn speed-up! []
  (set-speed! speed-step))

(defn slow-down! []
  (set-speed! (* speed-step -1)))

(defn set-paragraph-id! [p i]
  (let [p-id (str "transcript-p-" i)]
    (log :debug "Updating id of paragraph from" (.-id p) "to" p-id)
    (set! (.-id p) p-id)
    (doseq [child (.-childNodes p)]
      (let [id (str/replace (.-id child) #"\d+" (str i))]
        (log :debug "Updating id of child from" (.-id child) "to" id)
        (set! (.-id child) id)))
    (save-paragraph! p)))

(defn insert-paragraph! [p]
  (let [i (re-find #"\d+$" (.-id p))]
    (if (not i)
      (log :error "Could not parse paragraph number from element:" p)
      (let [i (js/parseInt i)]
        (dom/set-children! p (create-transcript-spans i {:ts "12:34:56", :text ""}))
        (loop [p p
               i (inc i)]
          (when p
            (set-paragraph-id! p i)
            (recur (.-nextSibling p) (inc i))))
        (inc-num-paragraphs!)))))

(defn handle-input! [ev]
  (let [sel (.getSelection js/window)
        el (.-anchorNode sel)
        parent (.-parentNode el)
        input-type (.-inputType ev)]
    (log :debug "Got input event type" input-type "on element" parent)
    (if (= "insertParagraph" input-type)
      (insert-paragraph! parent)
      (save-key! (.-id parent) (.-textContent el)))))

(defn handle-audio-url-button! [_ev]
  (when-let [url (not-empty (dom/get-value "#audio-url"))]
    (open-audio-url! (dom/get-el "audio") url)))

(defn handle-transcript-url-button! [ev]
  (when-let [url (not-empty (dom/get-value "#transcript-url"))]
    (import-transcript-url! (dom/get-el "#textbox") url)))

(defn handle-enter! [on-enter-fn ev]
  (when (= "Enter" (.-key ev))
    (log :debug "Enter pressed")
    (on-enter-fn)))

(defn handle-global-keys! [ev]
  (let [handle! (fn [f]
                  (log :debug "Key pressed:" (.-key ev))
                  (f)
                  (.preventDefault ev))]
    (case (.-key ev)
      "Escape" (handle! play-pause!)
      "F1" (handle! seek-backward!)
      "F2" (handle! seek-forward!)
      "F3" (handle! slow-down!)
      "F4" (handle! speed-up!)
      "j" (when (.-ctrlKey ev)
            (handle! #(log :debug "Insert timestamp")))
      :unmapped-key)))

(defn init-ui! []
  (let [transcript-el (dom/get-el "#textbox")]
    (dom/clear-listeners! state)
    (dom/add-listener! state "#close-message-button" "click"
                       hide-message!)
    (dom/add-listener! state "#import" "change"
                       (partial read-transcript-file! transcript-el))
    (dom/add-listener! state "#audio-file" "change"
                       (partial load-audio! (dom/get-el "audio")))
    (dom/add-listener! state "#export" "click"
                       export-transcript!)
    (dom/add-listener! state "#textbox" "input"
                       handle-input!)
    (dom/add-listener! state "#audio-url-button" "click"
                       handle-audio-url-button!)
    (dom/add-listener! state "#audio-url" "keydown"
                       (partial handle-enter! handle-audio-url-button!))
    (dom/add-listener! state "#transcript-url-button" "click"
                       handle-transcript-url-button!)
    (dom/add-listener! state "#transcript-url" "keydown"
                       (partial handle-enter! handle-transcript-url-button!))
    (dom/add-listener! state "audio" "durationchange"
                       #(do
                          (log :debug "Audio loaded; duration:" (get-audio-duration))
                          (display-audio!)
                          (swap! state assoc :paused true)))
    (dom/add-listener! state "audio" "error"
                       handle-audio-error!)
    (dom/add-listener! state "audio" "ratechange"
                       display-audio-playback-rate!)
    (dom/add-listener! state "audio" "timeupdate"
                       display-audio-ts!)
    (dom/add-listener! state js/document "keydown"
                       handle-global-keys!)
    (restore-transcript! transcript-el)))

(comment

  (init-ui!)

  (js/console.clear)

  )
