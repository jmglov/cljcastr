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

(defonce state (atom {:ops []}))

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

(defn save-operation! [op]
  (log :debug "Saving operation:" (clj->js op))
  (swap! state update :ops #(-> op (cons %) vec))
  op)

(defn pop-operation! []
  (let [[op & ops] (:ops @state)]
    (swap! state assoc :ops (vec ops))
    op))

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
  (let [ts (if (string? ts) (time/ts->sec ts) ts)]
    (set! (.-currentTime (dom/get-el "audio")) ts)))

(defn get-audio-playback-rate []
  (-> (dom/get-el "audio") .-playbackRate))

(defn set-audio-playback-rate! [rate]
  (set! (.-playbackRate (dom/get-el "audio")) rate))

(defn clear-storage! []
  (let [storage (.-localStorage js/window)]
    (log :debug "Clearing" (.-length storage) "keys from local storage")
    (.clear storage)))

(defn load-key [k]
  (-> (.-localStorage js/window)
      (.getItem k)))

(defn save-key! [k v]
  (log :debug "Saving key" k "to local storage:" v)
  (-> (.-localStorage js/window)
      (.setItem k v)))

(defn remove-key! [k]
  (log :debug "Removing key" k "from local storage")
  (-> (.-localStorage js/window)
      (.removeItem k)))

(defn load-num-paragraphs []
  (-> (load-key "transcript-num-paragraphs")
      js/parseInt))

(defn save-num-paragraphs! [n]
  (save-key! "transcript-num-paragraphs" (str n)))

(defn inc-num-paragraphs! []
  (let [n (load-num-paragraphs)]
    (save-num-paragraphs! (inc n))))

(defn save-el! [el]
  (save-key! (.-id el) (dom/get-text el)))

(defn save-paragraph! [p-el]
  (doseq [el (.-childNodes p-el)]
    (save-el! el)))

(defn load-transcript-filename []
  (load-key "transcript-filename"))

(defn save-transcript-filename! [filename]
  (swap! state assoc :transcript-filename filename)
  (swap! state dissoc :transcript-url)
  (save-key! "transcript-filename" filename)
  (remove-key! "transcript-url"))

(defn load-transcript-url []
  (load-key "transcript-url"))

(defn save-transcript-url! [url]
  (swap! state assoc :transcript-url url)
  (swap! state dissoc :transcript-filename)
  (save-key! "transcript-url" url)
  (remove-key! "transcript-filename"))

(defn load-audio-filename []
  (load-key "audio-filename"))

(defn save-audio-filename! [filename]
  (swap! state assoc :audio-filename filename)
  (swap! state dissoc :audio-url)
  (save-key! "audio-filename" filename)
  (remove-key! "audio-url"))

(defn load-audio-url [url]
  (load-key "audio-url" url))

(defn save-audio-url! [url]
  (swap! state assoc :audio-url url)
  (swap! state dissoc :audio-filename)
  (save-key! "audio-url" url)
  (remove-key! "audio-filename"))

(defn read-transcript [text]
  (-> (edn/read-string text)
      :transcript))

(defn ts-el? [el]
  (when-let [id (.-id el)]
    (re-matches #"transcript-p-\d+-ts" id)))

(defn speaker-el? [el]
  (when-let [id (.-id el)]
    (re-matches #"transcript-p-\d+-speaker" id)))

(defn text-el? [el]
  (when-let [id (.-id el)]
    (re-matches #"transcript-p-\d+-text" id)))

(defn transcript-p-id [i]
  (str "transcript-p-" i))

(defn get-transcript-p [i]
  (dom/get-el (str "#" (transcript-p-id i))))

(defn get-paragraph-parent [el]
  (let [id (or (.-id el) "")
        parent (.-parentNode el)]
    (if (re-matches #"transcript-p-\d+$" id)
      el
      (when parent
        (get-paragraph-parent parent)))))

(defn get-paragraph-el [p k]
  (dom/get-el p (str ".transcript-" (name k))))

(defn get-paragraph-num [el]
  (when-let [p (get-paragraph-parent el)]
    (->> p .-id (re-find #"\d+") js/parseInt)))

(defn get-ts [p]
  (-> (get-paragraph-el p :ts)
      dom/get-text
      not-empty))

(defn get-speaker [p]
  (-> (get-paragraph-el p :speaker)
      dom/get-text
      not-empty))

(defn get-text [p]
  (-> (get-paragraph-el p :text)
      dom/get-text
      not-empty))

(defn get-current-paragraph-num []
  (-> js/window .getSelection .-anchorNode get-paragraph-num))

(defn get-location []
  (let [sel (.getSelection js/window)
        el (.-anchorNode sel)
        offset (.-anchorOffset sel)
        parent (.-parentNode el)
        paragraph (get-paragraph-parent el)
        paragraph-num (get-paragraph-num paragraph)]
    {:sel sel
     :el el
     :parent parent
     :paragraph paragraph
     :paragraph-num paragraph-num
     :offset offset
     :in-speaker (some speaker-el? [el parent])
     :in-text (some text-el? [el parent])}))

(defn transcript-el-id [i k]
  (str "transcript-p-" i "-" (name k)))

(defn get-transcript-el [i k]
  (dom/get-el (str "#" (transcript-el-id i k))))

(defn get-transcript-ts [i]
  (transcript-el-id i :ts))

(defn transcript-el-class [k]
  (str "transcript-" (name k)))

(defn load-paragraph [i]
  (log :debug "Loading paragraph" i "from local storage")
  (->> [:ts :speaker :text]
       (map (fn [k]
              [k (load-key (transcript-el-id i k))]))
       (into {})))

(defn load-transcript []
  (let [num-paragraphs (load-num-paragraphs)]
    (when (pos-int? num-paragraphs)
      (log :info "Loading transcript from local storage; restoring"
           num-paragraphs "paragraphs")
      (->> (range num-paragraphs)
           (map load-paragraph)))))

(declare combine-paragraphs!)

(defn handle-text-keydown! [ev]
  (let [k (.-key ev)
        el (.-target ev)
        p (get-paragraph-parent el)
        {:keys [offset paragraph-num]} (get-location)]
    (log :debug "Key pressed; offset:" offset ev)

    (case k
      ;; Don't allow enter to be pressed at the beginning of text elements to
      ;; prevent blank paragraphs being inserted
      "Enter"
      (when (= 0 offset)
        (.preventDefault ev))

      ;; If backspace is pressed at the beginning of a text element in a
      ;; paragraph without a speaker, combine with the previous paragraph
      "Backspace"
      (when (and (= 0 offset)
                 (> paragraph-num 0)
                 (not (get-speaker p)))
        (combine-paragraphs! (dec paragraph-num) paragraph-num)
        (.preventDefault ev))

      "z"
      (when (.-ctrlKey ev)
        (when-let [op (pop-operation!)]
          (case (:type op)
            :insert-paragraph
            (let [{:keys [new-paragraph-num]} op]
              (log :debug "Undoing insert paragraph" new-paragraph-num)
              (combine-paragraphs! (dec new-paragraph-num) new-paragraph-num))

            :insert-timestamp
            (let [{:keys [paragraph-num]} op]
              (log :debug "Undoing insert timestamp for paragraph" paragraph-num)
              (-> (get-transcript-el paragraph-num :ts)
                  (dom/set-text! "")))

            :remove-timestamp
            (let [{:keys [paragraph-num ts]} op]
              (log :debug "Undoing insert timestamp for paragraph" paragraph-num)
              (-> (get-transcript-el paragraph-num :ts)
                  (dom/set-text! ts))))

          ;; Stop the undo from propagating
          (.preventDefault ev)))

      :ignored)))

(defn create-transcript-el [i [k v]]
  (let [el (dom/create-el
            "div"
            {:id (transcript-el-id i k)
             :classes ["transcript" (transcript-el-class k)]})]
    (set! (.-innerText el) (or v ""))
    (dom/set-attribute! el "contenteditable" (if (= :ts k) "false" "true"))

    ;; Add a click handler to all ts elements that seeks to the specified
    ;; timestamp, if any.
    (when (= :ts k)
      (.addEventListener el "click"
                         (fn [ev]
                           (when-let [ts (-> ev .-target dom/get-text) not-empty]
                             (log :debug "Seeking audio to timestamp:" ts)
                             (set-audio-ts! ts)))))

    ;; Don't allow enter to be pressed in speaker elements to prevent new
    ;; paragraphs being inserted
    (when (= :speaker k)
      (.addEventListener el "keydown"
                         #(when (= "Enter" (.-key %))
                            (.preventDefault %))))

    (when (= :text k)
      (.addEventListener el "keydown" handle-text-keydown!))

    el))

(defn create-transcript-els [i paragraph]
  (->> [:ts :speaker :text]
       (map (fn [k] (create-transcript-el i [k (get paragraph k)])))
       (remove nil?)))

(defn create-transcript-p [i paragraph]
  (let [p (dom/create-el "div" {:id (str "transcript-p-" i)
                                :class "transcript-p"})]
    (dom/set-children! p (create-transcript-els i paragraph))
    p))

(defn update-paragraph! [i p-data]
  (let [p (get-transcript-p i)]
    (doseq [k [:ts :speaker :text]]
      (dom/set-text! (get-transcript-el i k) (p-data k)))
    p))

(defn transcript->elements [transcript]
  (map-indexed create-transcript-p transcript))

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
  (dom/set-styles! (dom/get-el "#audio-controls") "display: flex;"))

(defn restore-transcript! [target-el]
  (when-not (get-transcript-p 0)
    (display-transcript! target-el (load-transcript))
    (show-message! "Transcript restored from local storage")))

(defn save-edn! [filename data]
  (let [a (dom/create-el "a")
        blob (js/Blob. [(pr-str data)] (clj->js {:type "application/edn"}))]
    (set! (.-href a) (js/URL.createObjectURL blob))
    (set! (.-download a) filename)
    (.click a))
  (show-message! (str "Transcript saved to file: " filename)))

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
    (save-transcript-filename! filename))
  (show-message! (str "Transcript imported from file: " filename)))

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
        (save-transcript-url! url)
        (show-message! (str "Transcript imported from URL: " url)))
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
        filename (.-name file)
        url (js/URL.createObjectURL file)]
    (set! (.-src audio-el) url)
    (save-audio-filename! filename)
    (display-audio!)
    (swap! state assoc :paused true)))

(defn open-audio-url! [audio-el url]
  (log :debug "Loading audio from URL:" url)
  (save-audio-url! url)
  (set! (.-src audio-el) url))

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
      (if (:playing @state)
        (do
          (log :debug "Pausing audio at" pos)
          (.pause audio)
          (dom/set-styles! "#play-button" "display: block;")
          (dom/set-styles! "#pause-button" "display: none;")
          (swap! state assoc :playing false))
        (do
          (log :debug "Playing audio from" pos)
          (.play audio)
          (dom/set-styles! "#play-button" "display: none;")
          (dom/set-styles! "#pause-button" "display: block;")
          (swap! state assoc :playing true))))))

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

(defn update-paragraph-ids! [start op]
  (let [f (case op
            :inc inc
            :dec dec)]
    (loop [p (get-transcript-p start)
           paragraph-num (f start)]
      (if p
        (do
          (set-paragraph-id! p paragraph-num)
          (save-paragraph! p)
          (recur (.-nextSibling p) (inc paragraph-num)))
        (save-num-paragraphs! paragraph-num)))))

(defn inc-paragraph-ids! [start]
  (update-paragraph-ids! start :inc))

(defn dec-paragraph-ids! [start]
  (update-paragraph-ids! start :dec))

(defn insert-paragraph! [{:keys [cur-text cur-p new-paragraph-num new-p]}]
  ;; Update text of current paragraph
  (let [el (get-paragraph-el cur-p :text)]
    (dom/set-text! el cur-text)
    (save-el! el))

  ;; Insert the new paragraph
  (log :debug "Inserting paragraph" new-paragraph-num new-p)
  (inc-paragraph-ids! new-paragraph-num)
  (dom/insert-child-after! cur-p new-p)
  (save-paragraph! new-p)

  ;; Remember this insertion so we can undo it
  (save-operation! {:type :insert-paragraph
                    :new-paragraph-num new-paragraph-num})

  (.focus (get-transcript-el new-paragraph-num :text)))

(defn insert-paragraph-from-input! [el]
  (let [cur-paragraph-num (get-paragraph-num el)
        cur-text (-> el (dom/get-child 0) dom/get-text str/trim)
        cur-p (get-paragraph-parent el)
        new-paragraph-num (inc cur-paragraph-num)
        new-text (-> el .-lastChild dom/get-text str/trim)
        new-p (create-transcript-p new-paragraph-num {:text new-text})]
    (insert-paragraph! {:el el
                        :cur-text cur-text
                        :cur-p cur-p
                        :new-paragraph-num new-paragraph-num
                        :new-p new-p})))


(defn delete-paragraph! [paragraph-num]
  (let [p (get-transcript-p paragraph-num)]
    (dom/remove-child! p))
  (dec-paragraph-ids! (inc paragraph-num)))

(defn combine-paragraphs! [i j]
  (if (= 1 (Math/abs (- i j)))
    (let [prev-i (load-paragraph i)
          prev-j (load-paragraph j)
          i-text-el (get-transcript-el i :text)
          j-text-el (get-transcript-el j :text)
          new-text (->> [i-text-el j-text-el]
                        (map dom/get-text)
                        (str/join " "))]
      (dom/set-text! i-text-el new-text)
      (delete-paragraph! j)
      (-> i-text-el .-firstChild (dom/move-cursor! :end))
      (save-key! (transcript-el-id i :text) new-text))
    (error! (str "Only adjacent paragraphs can be combined; "
                 "attempted to combine " i " and " j))))

(defn undo-combine-paragraphs! [i prev-i prev-j]
  (log :debug "Undo combining paragraphs" i "and" (inc i)
       (clj->js prev-i) (clj->js prev-j))
  (let [j (inc i)
        cur-p (get-transcript-p i)
        new-p (create-transcript-p j prev-j)]
    (inc-paragraph-ids! j)
    (dom/insert-child-after! cur-p new-p)
    (save-paragraph! new-p)
    (->> prev-i (update-paragraph! i) save-paragraph!)
    (pop-operation!)))

;; select-text! must be declared so it can refer to itself when unregistering
(declare select-text!)

(defn select-example-text! [ev]
  (let [el (.-target ev)]
    (when (dom/has-class? el "example")
      (dom/select-el! el))))

(defn select-text! [ev]
  (select-example-text! ev)
  (let [el (.-target ev)]
    (dom/clear-listeners! state el (.-type ev))))

(defn handle-input! [ev]
  (let [el (.-target ev)
        input-type (.-inputType ev)
        p (get-paragraph-parent el)]
    (log :debug "Got input event:" ev)
    (cond
      (= "insertParagraph" input-type)
      (insert-paragraph-from-input! el)

      :else
      (do
        (save-key! (.-id el) (dom/get-text el))
        (pop-operation!)))
    (dom/remove-class! el "example")
    (when-not (get-paragraph-el p :ts)
      (remove-key! (transcript-el-id (get-paragraph-num p) :ts)))))

(defn insert-timestamp! []
  (let [{:keys [in-speaker in-text offset] :as loc} (get-location)
        ts (time/sec->ts (get-audio-ts) true)]
    (if (or in-speaker (and in-text (= 0 offset)))
      (let [{:keys [paragraph paragraph-num]} loc
            ts-el (get-paragraph-el paragraph :ts)]
        (log :debug "Adding timestamp to paragraph" paragraph-num)
        (dom/set-text! ts-el ts)
        (save-el! ts-el)
        (save-operation! {:type :insert-timestamp
                          :paragraph-num paragraph-num}))
      (let [{:keys [el paragraph paragraph-num]} loc
            cur-text (-> el dom/get-text (subs 0 offset) str/trim)
            new-text (-> el dom/get-text (subs offset) str/trim)
            new-paragraph-num (inc paragraph-num)]
        (insert-paragraph!
         {:cur-text cur-text
          :cur-p paragraph
          :new-paragraph-num new-paragraph-num
          :new-p (create-transcript-p new-paragraph-num
                                      {:ts ts, :text new-text})})))))

(defn remove-timestamp! []
  (let [{:keys [paragraph paragraph-num]} (get-location)
        ts-el (get-paragraph-el paragraph :ts)
        ts (dom/get-text ts-el)]
    (log :debug "Removing timestamp from paragraph" paragraph-num)
    (dom/set-text! ts-el "")
    (save-el! ts-el)
    (save-operation! {:type :remove-timestamp
                      :paragraph-num paragraph-num
                      :ts ts})))

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
                  (try
                    (f)
                    (catch :default e
                      (log :error e)))
                  (.preventDefault ev))]
    (case (.-key ev)
      "Escape" (handle! play-pause!)
      "F1" (handle! seek-backward!)
      "F2" (handle! seek-forward!)
      "F3" (handle! slow-down!)
      "F4" (handle! speed-up!)
      "j" (when (.-ctrlKey ev)
            (handle! insert-timestamp!))
      "k" (when (.-ctrlKey ev)
            (handle! remove-timestamp!))
      :unmapped-key)))

(defn init-transcript!
  "If no transcript is available in local storage, create one."
  [transcript-el]
  (when-not (dom/get-children transcript-el)
    (let [p (create-transcript-p 0 {:ts "00:00"
                                    :speaker "Speaker 1"
                                    :text "Insert text here"})]
      (dom/set-child! transcript-el p))
    (let [speaker-el (get-transcript-el 0 :speaker)
          text-el (get-transcript-el 0 :text)]
      (dom/add-class! speaker-el "example")
      (dom/add-listener! state speaker-el "click" select-text!)
      (dom/add-listener! state speaker-el "focus" select-example-text!)
      (dom/add-class! text-el "example")
      (dom/add-listener! state text-el "click" select-text!)
      (dom/add-listener! state text-el "focus" select-example-text!)
      (.focus speaker-el)
      (dom/select-el! speaker-el))
    (save-transcript! transcript-el)
    (save-num-paragraphs! 1)))

(defn load-query-params! []
  (let [params (js/URLSearchParams. js/window.location.search)
        audio-url (.get params "audio-url")
        transcript-url (.get params "transcript-url")]
    (when audio-url
      (dom/set-value! "#audio-url" audio-url))
    (when transcript-url
      (dom/set-value! "#transcript-url" transcript-url))))

(defn add-audio-listeners! [audio-el]
  (dom/add-listener! state audio-el "durationchange"
                     #(let [src (or (:audio-filename @state) (:audio-url @state))
                            src-type (if (:audio-filename @state) "file" "URL")]
                        (log :debug "Audio loaded; duration:" (get-audio-duration))
                        (display-audio!)
                        (swap! state assoc :paused true)
                        (show-message! (str "Loaded audio from " src-type ": " src))))
  (dom/add-listener! state audio-el "error"
                     handle-audio-error!)
  (dom/add-listener! state audio-el "ratechange"
                     display-audio-playback-rate!)
  (dom/add-listener! state audio-el "timeupdate"
                     display-audio-ts!))

(defn add-import-listeners! [transcript-el]
  (dom/add-listener! state "#import" "change"
                     (partial read-transcript-file! transcript-el))
  (dom/add-listener! state "#audio-file" "change"
                     (partial load-audio! (dom/get-el "audio")))
  (dom/add-listener! state "#audio-url-button" "click"
                     handle-audio-url-button!)
  (dom/add-listener! state "#audio-url" "keydown"
                     (partial handle-enter! handle-audio-url-button!))
  (dom/add-listener! state "#transcript-url-button" "click"
                     handle-transcript-url-button!)
  (dom/add-listener! state "#transcript-url" "keydown"
                     (partial handle-enter! handle-transcript-url-button!)))

(defn add-export-listeners! [transcript-el]
  (dom/add-listener! state "#export" "click"
                     export-transcript!)
  (dom/add-listener! state "#clear" "click"
                     (fn [_ev]
                       (clear-storage!)
                       (dom/clear-children! transcript-el)
                       (init-transcript! transcript-el)
                       (show-message! "Transcript cleared"))))

(defn add-input-listeners! [transcript-el]
  (dom/add-listener! state transcript-el "input"
                     handle-input!))

(defn add-key-listeners! []
  (dom/add-listener! state js/document "keydown"
                     handle-global-keys!))

(defn add-message-listeners! []
  (dom/add-listener! state "#close-message-button" "click" hide-message!))

(defn init-ui! []
  (let [transcript-el (dom/get-el "#textbox")]
    (load-query-params!)
    (dom/clear-listeners! state)
    (add-import-listeners!)
    (add-export-listeners! transcript-el)
    (add-input-listeners! transcript-el)
    (add-audio-listeners! "audio")
    (add-key-listeners!)
    (add-message-listeners!)
    (restore-transcript! transcript-el)
    (init-transcript! transcript-el)))

(when-not (:initialised @state)
  (init-ui!)
  (swap! state assoc :initialised true))

(comment

  (init-ui!)

  (js/console.clear)

  )
