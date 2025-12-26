(ns cljcastr.player
  (:require [cljcastr.dom :as dom]
            [cljcastr.transcript :as transcript]
            [clojure.string :as str]
            [promesa.core :as p]))

(defonce state (atom {}))

(def svg-ns "http://www.w3.org/2000/svg")

(def default-opts {:color-played "#ff9800"
                   :color-buffered "#ffbd52"
                   :color-position "black"
                   :enabled-buttons [:shuffle
                                     :back
                                     :rewind
                                     :play
                                     :pause
                                     :stop
                                     :fast-forward
                                     :next
                                     :repeat
                                     :repeat-one]
                   :title-fmt [:episode/artist " - " :episode/title]
                   :volume-image "/img/volume.png"
                   :audio-selector "#cljcastr-player-audio"
                   :controls-selector "#cljcastr-player-controls"
                   :cover-selector "#cover-image"
                   :main-selector "#wrapper"
                   :timeline-canvas-selector "canvas.cljcastr-player-timeline"
                   :timeline-position-selector "#cljcastr-player-position"
                   :title-selector "#title"})

(def buttons
  {
   :shuffle
   {:label "Toggle shuffle"
    :paths
    [["drop-shadow"
      "M116 4H12c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8V12c0-4.42-3.58-8-8-8z"]
     ["bg"
      "M110.16 3.96h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.46c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
     ["shine"
      "M40.16 12.86c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
      ".75"]
     ["fg"
      "M43.7 62.21v-25.7a2.258 2.258 0 0 1 3.4-2l43.5 25.7c1.13.72 1.47 2.22.75 3.35c-.19.3-.45.55-.75.75l-43.5 25.6c-1.08.63-2.46.27-3.09-.81c-.21-.36-.32-.77-.31-1.19v-25.7z"]]}

   :back
   {:label "Back episode"
    :paths
    [["drop-shadow"
      "M116.46 3.96h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8v-104c0-4.42-3.58-8-8-8z"]
     ["bg"
      "M110.16 3.96h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.46c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
     ["fg"
      "M108.46 63.96v-25.7c0-1.8-1.7-2.9-3-2l-35 24.4v-22.4c.13-1.13-.69-2.15-1.82-2.28c-.45-.05-.9.05-1.28.28l-36.9 25.1v-23.5a1.9 1.9 0 0 0-1.9-1.9h-10.2a1.9 1.9 0 0 0-1.9 1.9v52.3c0 1.05.85 1.9 1.9 1.9h10.3a1.9 1.9 0 0 0 1.9-1.9v-23.6l36.9 25c.98.58 2.24.26 2.82-.72c.23-.39.33-.84.28-1.28v-22.3l35 24.4c1.4.9 3-.2 3-2l-.1-25.7z"]
     ["shine"
      "M40.16 12.86c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 6.5-3 13.3c0 4.8 0 7.3 2.5 7.3c3.4 0 3.4-5.9 6.2-10.3c5.4-8.7 18.9-8.6 18.9-11.6z"
      ".75"]]}

   :rewind
   {:label "Rewind"
    :paths
    [["drop-shadow"
      "M116.46 4h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8V12c0-4.42-3.58-8-8-8z"]
     ["bg"
      "M110.16 4h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.5c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
     ["shine"
      "M40.16 12.9c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
      ".75"]
     ["fg"
      "M104.46 64V38.3c0-1.8-1.7-2.9-3-2l-37 24.4V38.3c.13-1.13-.69-2.15-1.82-2.28c-.45-.05-.9.05-1.28.28L21.46 62a2.529 2.529 0 0 0 0 4.1l39.8 25.7c.98.58 2.24.26 2.82-.72c.23-.39.33-.84.28-1.28V67.3l37 24.4c1.4.9 3-.2 3-2l.1-25.7z"]]}

   :play
   {:label "Play"
    :paths
    [["drop-shadow"
      "M116.46 3.96h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8v-104c0-4.42-3.58-8-8-8z"]
     ["bg"
      "M110.16 3.96h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.46c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
     ["shine"
      "M40.16 12.86c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
      ".75"]
     [:el "g"
      :paths
      [["fg"
        "M43.7 62.21v-25.7a2.258 2.258 0 0 1 3.4-2l43.5 25.7c1.13.72 1.47 2.22.75 3.35c-.19.3-.45.55-.75.75l-43.5 25.6c-1.08.63-2.46.27-3.09-.81c-.21-.36-.32-.77-.31-1.19v-25.7z"]]]]}

   :pause
   {:label "Pause"
    :paths
    [["drop-shadow"
      "M116.46 3.96h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8v-104c0-4.42-3.58-8-8-8z"]
     ["bg"
      "M110.16 3.96h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.46c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
     ["shine"
      "M40.16 12.86c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
      ".75"]
     ["fg"
      "M54.46 91.96h-12c-1.1 0-2-.9-2-2v-52c0-1.1.9-2 2-2h12c1.1 0 2 .9 2 2v52a2 2 0 0 1-2 2z"]
     ["fg"
      "M86.46 91.96h-12c-1.1 0-2-.9-2-2v-52c0-1.1.9-2 2-2h12c1.1 0 2 .9 2 2v52a2 2 0 0 1-2 2z"]
     ]}

   :stop
   {:label "Stop"
    :paths [["drop-shadow"
             "M116.46 3.96h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8v-104c0-4.42-3.58-8-8-8z"]
            ["bg"
             "M110.16 3.96h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.46c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
            ["shine"
             "M40.16 12.86c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
             ".75"]
            ["fg"
             "M89.66 91.96h-50.4c-1.55 0-2.8-1.25-2.8-2.8v-50.4c0-1.55 1.25-2.8 2.8-2.8h50.4a2.728 2.728 0 0 1 2.8 2.66v50.54a2.728 2.728 0 0 1-2.66 2.8h-.14z"]
            ]}

   :fast-forward
   {:label "Fast forward"
    :paths [
            ["drop-shadow"
             "M116.46 4.14h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8v-104c0-4.42-3.58-8-8-8z"]
            ["bg"
             "M110.16 4.14h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.64c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
            ["shine"
             "M40.16 13.04c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
             ".75"]
            ["fg"
             "M107.46 62.14l-39.9-25.7c-.98-.58-2.24-.26-2.82.72c-.23.39-.33.84-.28 1.28v22.3l-37-24.3c-1.4-.9-3 .2-3 2v51.4c0 1.8 1.7 2.9 3 2l37-24.4v22.3c-.13 1.13.69 2.15 1.82 2.28c.45.05.9-.05 1.28-.28l39.9-25.6c1.1-.76 1.38-2.28.62-3.38c-.17-.25-.38-.46-.62-.62z"]
            ["fg"
             "M107.36 66.14l-39.8 25.7c-.35.24-.78.34-1.2.3a.632.632 0 0 1-.4-.1c-.1 0-.2-.1-.4-.2c-.24-.11-.45-.28-.6-.5c-.12-.1-.2-.24-.2-.4c-.2-.33-.31-.71-.3-1.1v-22.4l-3 2l-34 22.4c-.35.24-.78.34-1.2.3a.632.632 0 0 1-.4-.1c-.1 0-.2-.1-.4-.2c-.7-.46-1.12-1.26-1.1-2.1v-51.3c-.08-1 .54-1.91 1.5-2.2c.1 0 .2-.1.4-.1c.42-.04.85.06 1.2.3l34 22.4l3 2v-22.4c0-.42.11-.83.3-1.2c.1-.1.1-.2.2-.4c.17-.2.38-.36.6-.5c.1-.1.2-.1.4-.2s.3-.1.4-.1c.42-.04.85.06 1.2.3l39.8 25.8c1.1.76 1.38 2.28.62 3.38c-.17.24-.38.45-.62.62z"
             ".2"]
            ]}

   :next
   {:label "Next episode"
    :paths [
            ["drop-shadow"
             "M116.46 4h-104c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8V12c0-4.42-3.58-8-8-8z"]
            ["bg"
             "M110.16 4h-98.2a7.555 7.555 0 0 0-7.5 7.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H110.06c4.14.01 7.49-3.34 7.5-7.48V11.5c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
            ["fg"
             "M107.56 36h-10.2a1.9 1.9 0 0 0-1.9 1.9v23.5l-35.9-25c-.98-.58-2.24-.26-2.82.72c-.23.39-.33.84-.28 1.28v22.3l-35-24.4c-1.4-.9-3 .2-3 2v51.4c0 1.8 1.7 2.9 3 2l35-24.4v22.3c-.13 1.13.69 2.15 1.82 2.28c.45.05.9-.05 1.28-.28l35.9-25v23.5c0 1.05.85 1.9 1.9 1.9h10.3a1.9 1.9 0 0 0 1.9-1.9V37.9c-.05-1.07-.93-1.9-2-1.9z"]
            ["shine"
             "M40.16 12.9c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
             ".75"]
            ]}

   :repeat
   {:label "Toggle repeat"
    :paths [
            ["drop-shadow"
             "M116 4H12c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8V12c0-4.42-3.58-8-8-8z"]
            ["bg"
             "M109.7 4H11.5A7.555 7.555 0 0 0 4 11.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H109.6c4.14.01 7.49-3.34 7.5-7.48V11.5c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
            ["shine"
             "M39.7 12.9c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
             ".75"]
            ["fg"
             "M83.6 67a3.996 3.996 0 0 1-5.64-.44c-.61-.71-.95-1.62-.96-2.56V50c0-1.1-.9-2-2-2H41c-4.8 0-11.5 1.5-12 12L14.2 73.3A36.01 36.01 0 0 1 13 64c0-17.7 12.5-32 28-32h34c1.1 0 2-.9 2-2V16a4 4 0 0 1 6.6-3l24 24a3.994 3.994 0 0 1 .35 5.65c-.11.13-.23.24-.35.35l-24 24z"]
            ["fg"
             "M38.4 61a3.996 3.996 0 0 1 5.64.44c.61.71.95 1.62.96 2.56v14c0 1.1.9 2 2 2h34c4.8 0 11.6-1.5 12-12l14.8-13.3c.81 3.03 1.21 6.16 1.2 9.3c0 17.7-12.5 32-28 32H47c-1.1 0-2 .9-2 2v14a4 4 0 0 1-6.6 3l-24-24a3.994 3.994 0 0 1-.35-5.65c.11-.13.23-.24.35-.35l24-24z"]
            ]}

   :repeat-one
   {:label "Toggle repeat one"
    :paths [
            ["drop-shadow"
             "M116 4H12c-4.42 0-8 3.58-8 8v104c0 4.42 3.58 8 8 8h104c4.42 0 8-3.58 8-8V12c0-4.42-3.58-8-8-8z"]
            ["bg"
             "M109.7 4H11.5A7.555 7.555 0 0 0 4 11.5v97.9c-.01 4.14 3.34 7.49 7.48 7.5H109.6c4.14.01 7.49-3.34 7.5-7.48V11.5c.09-4.05-3.13-7.41-7.18-7.5h-.22z"]
            ["shine"
             "M39.7 12.9c0-2.3-1.6-3-10.8-2.7c-7.7.3-11.5 1.2-13.8 4s-2.9 8.5-3 15.3c0 4.8 0 9.3 2.5 9.3c3.4 0 3.4-7.9 6.2-12.3c5.4-8.7 18.9-10.6 18.9-13.6z"
             ".75"]
            ["fg"
             "M108.8 54.7L94 68c-.4 10.5-7.2 12-12 12H68.8c1.6 5.21 1.6 10.79 0 16H82c15.5 0 28-14.3 28-32c.01-3.14-.4-6.27-1.2-9.3z"]
            ["fg"
             "M108.6 37l-24-24a3.996 3.996 0 0 0-5.64.44c-.61.71-.95 1.62-.96 2.56v14c0 1.1-.9 2-2 2H42c-15.5 0-28 14.3-28 32c-.01 3.14.4 6.27 1.2 9.3l.1-.1c3.03-6.02 8.2-10.7 14.5-13.1l.2-.1c.5-10.5 7.2-12 12-12h34c1.1 0 2 .9 2 2v14a4 4 0 0 0 6.6 3l24-24a3.863 3.863 0 0 0 0-6z"]
            ["fg"
             "M40.7 63.4c-13.03 0-23.6 10.57-23.6 23.6s10.57 23.6 23.6 23.6S64.3 100.03 64.3 87c-.02-13.02-10.58-23.58-23.6-23.6zm5.3 42.2c0 .55-.45 1-1 1h-6.7c-.55 0-1-.45-1-1V80.5c-.03-.55-.5-.97-1.05-.95c-.08 0-.17.02-.25.05l-5.9 1.7a.992.992 0 0 1-1.25-.65c-.03-.08-.04-.16-.05-.25v-4.5c.02-.42.3-.78.7-.9l15.1-5a.992.992 0 0 1 1.25.65c.03.08.04.16.05.25l.1 34.7z"]
            ]}
   })

(defn ->int [x]
  (if (number? x)
    x
    (js/parseInt x)))

(defn snake->kebab [s]
  (str/replace s "_" "-"))

(defn file-extension [path]
  (->> path
       (re-find #"^.+[.]([^.]+)$")
       last))

(defn parse-opts []
  (merge default-opts
         (->> js/CLJCASTR_PLAYER_OPTS
              js->clj
              (map (fn [[k v]]
                     (let [k (-> k snake->kebab keyword)
                           v (case k
                               :enabled-buttons
                               (->> v (map (comp keyword snake->kebab)) set)

                               :title-fmt
                               (->> v (map #(if (str/starts-with? % ":")
                                              (keyword (subs % 1))
                                              %)))

                               v)]
                       [k v])))
              (into {}))))

(defn log
  ([obj]
   (log nil obj {}))
  ([msg obj]
   (log msg obj {}))
  ([msg obj {:keys [log-atom] :as opts}]
   (when log-atom
     (swap! log-atom conj {:msg msg, :obj obj}))
   (if msg
     (js/console.log msg (clj->js obj))
     (js/console.log (clj->js obj)))
   obj))

(defn ns-keyword->path [kw]
  (let [ks (-> kw str (str/replace ":" "") (str/split "/"))]
    (mapv keyword ks)))

(defn fmt [fmt-str data]
  (->> fmt-str
       (map (fn [arg]
              (if (keyword? arg)
                (get-in data (ns-keyword->path arg))
                arg)))
       (apply str)))

(defn parse-xml [xml-str]
  (.parseFromString (js/window.DOMParser.) xml-str "text/xml"))

(defn fetch-xml [path]
  (p/->> (js/fetch (js/Request. path))
         (.text)
         parse-xml
         (log "Fetched XML:")))

(defn xml-get [el k]
  (when-let [html (-> (dom/get-el el k) dom/get-html)]
    (-> html
        (str/replace #"^<!\[CDATA\[(:?\n)?" "")
        (str/replace #"(:?\n)?\]\]>$" ""))))

(defn xml-get-attr [el k attr]
  (when-let [el (dom/get-el el k)]
    (.getAttribute el attr)))

(defn mk-svg-path
  ([cls d]
   (mk-svg-path cls d nil))
  ([cls d opacity]
   (let [path (js/document.createElementNS svg-ns "path")]
     (doto path
       (.setAttribute "d" d)
       (.setAttribute "class" cls))
     (if opacity
       (doto path
         (.setAttribute "opacity" opacity))
       path)))
  ([_ el-name _ paths]
   (dom/set-children! (js/document.createElementNS svg-ns el-name)
                      (map (partial apply mk-svg-path) paths))))

(defn mk-svg []
  (let [svg (js/document.createElementNS svg-ns "svg")]
    (doto svg
      (.setAttribute "viewBox" "0 0 128 128")
      (.setAttribute "preserveAspectRatio" "xMidYMid meet")
      (.setAttribute "aria-hidden" "true")
      (.setAttribute "role" "img")
      (.setAttribute "class" "control clickable"))))

(defn init-state [{:keys [single-episode] :as opts}
                  {:keys [episodes] :as podcast}]
  (let [episode-numbers (->> episodes
                             (map :number)
                             sort)
        state
        {:opts (merge default-opts opts)
         :podcast podcast
         :paused? true
         :shuffling? false
         :repeating? false
         :repeating-all? false
         :active-episode (if single-episode
                           (->int single-episode)
                           (first episode-numbers))
         :prev-episodes (list)
         :next-episodes (rest episode-numbers)}]
    (log "State initialised" state)
    state))

(defn get-episode-index [{:keys [active-episode podcast]}]
  (->> (:episodes podcast)
       (map-indexed (fn [i episode] [i (:number episode)]))
       (some (fn [[i number]]
               (and (= active-episode number) i)))))

(defn toggle-repeat [{:keys [repeating? repeating-all?] :as state}]
  (cond
    repeating-all?
    (assoc state
           :repeating? true
           :repeating-all? false)

    repeating?
    (assoc state :repeating? false)

    :else
    (assoc state :repeating-all? true)))

(defn toggle-shuffle [{:keys [podcast active-episode shuffling?] :as state}]
  (let [num-episodes (count (:episodes podcast))]
    (if shuffling?
      (assoc state
             :shuffling? false
             :next-episodes (range (inc active-episode) (inc num-episodes))
             :prev-episodes (range 1 active-episode))
      (assoc state
             :shuffling? true
             :next-episodes (->> (range 1 (inc num-episodes))
                                 (remove #(= active-episode %))
                                 shuffle)))))

(defn advance-episode [{:keys [active-episode next-episodes prev-episodes] :as state}]
  (let [next-episode (first next-episodes)]
    (assoc state
           :active-episode (or next-episode active-episode)
           :prev-episodes (cons active-episode prev-episodes)
           :next-episodes (rest next-episodes))))

(defn auto-advance-episode [{:keys [active-episode next-episodes prev-episodes
                                    repeating? repeating-all?]
                             :as state}]
  (let [next-episode (first next-episodes)]
    (cond
      repeating?
      state

      (and repeating-all? (not next-episode))
      (let [prev-episodes (cons active-episode prev-episodes)
            next-episodes (rest (reverse prev-episodes))
            active-episode (first (reverse prev-episodes))]
        (assoc state
               :active-episode active-episode
               :next-episodes next-episodes
               :prev-episodes prev-episodes))

      :else
      (advance-episode state))))

(defn back-episode [{:keys [active-episode next-episodes prev-episodes] :as state}]
  (if-let [prev-episode (first prev-episodes)]
    (assoc state
           :active-episode prev-episode
           :prev-episodes (rest prev-episodes)
           :next-episodes (cons active-episode next-episodes))
    state))

(defn move-to-episode [{:keys [podcast active-episode next-episodes prev-episodes shuffling?] :as state} n]
  (let [num-episodes (count (:episodes podcast))
        next-episodes (cond
                        (some #(= n %) next-episodes)
                        (->> next-episodes (drop-while #(not= n %)) rest)

                        shuffling?
                        next-episodes

                        :else
                        (range (inc n) (inc num-episodes)))]
    (assoc state
           :active-episode n
           :prev-episodes (if shuffling?
                            (cons active-episode prev-episodes)
                            (range 1 n))
           :next-episodes next-episodes)))

(defn ->episode [{:keys [artist] :as podcast} item-el]
  {:artist artist
   :title (xml-get item-el "title")
   :number (-> (xml-get item-el "episode") (or (xml-get item-el "bonusNumber")) ->int)
   :description (xml-get item-el "description")
   :transcript-url (xml-get item-el "transcriptUrl")
   :src (xml-get-attr item-el "enclosure" "url")})

(defn ->podcast [xml]
  (let [podcast {:artist (xml-get xml "author")
                 :title (xml-get xml "title")
                 :image (xml-get-attr xml "image" "href")}]
    (assoc podcast :episodes
           (->> (dom/get-els xml "item")
                (map (partial ->episode podcast))
                (sort-by :number)
                vec))))

(defn load-podcast [feed-url]
  (log "Loading feed URL:" feed-url)
  (p/->> (fetch-xml feed-url)
         ->podcast
         (log "Loaded podcast:")))

(defn get-episode-duration []
  (.-duration (:audio-el @state)))

(defn get-playback-position []
  (.-currentTime (:audio-el @state)))

(defn set-playback-position! [ ss]
  (set! (.-currentTime (:audio-el @state)) ss))

(defn format-pos
  ([pos]
   (format-pos pos false))
  ([pos drop-decimal?]
   (let [hh (-> pos (/ 3600) int)
         hh-str (if (zero? hh) "" (str hh ":"))
         mm (-> pos (/ 60) (mod 60) int)
         mm-str (-> (str mm) (str/replace #"^(\d)$" "0$1") (str ":"))
         ss (mod pos 60)
         ss-str (-> (str ss)
                    (str/replace #"^(\d+)$" "$1.00")
                    (str/replace #"^(\d)[.]" "0$1.")
                    (str/replace #"[.](\d{1,2})\d*" ".$1"))
         ss-str (if drop-decimal?
                  (str/replace ss-str #"[.]\d+$" "")
                  ss-str)]
     (str hh-str mm-str ss-str))))

(defn format-playback
  ([]
   (format-playback nil (get-playback-position)))
  ([num pos]
   (format-playback (or num (:active-episode @state))
                    pos
                    (get-episode-duration)))
  ([num pos dur]
   (str "episode " num
        " [" (format-pos pos) " / " (format-pos dur) "]")))

(defn format-playlist [state]
  (-> state
      (select-keys [:prev-episodes :active-episode :next-episodes])
      pr-str))

(defn get-buffered []
  (let [buffered (.-buffered (:audio-el @state))]
    (->> (range (.-length buffered))
         (map (fn [i]
                [(.start buffered i)
                 (.end buffered i)])))))

(defn get-seekable []
  (let [seekable (.-seekable (:audio-el @state))]
    (->> (range (.-length seekable))
         (map (fn [i]
                [(.start seekable i)
                 (.end seekable i)])))))

(defn draw-rect! [ctx x y w h color]
  (set! (.-fillStyle ctx) color)
  (.fillRect ctx x y w h))

(defn display-timeline!
  [{:keys [color-played color-buffered color-position timeline-position-selector]
    :as opts}]
  (let [canvas (:timeline-canvas-el @state)
        canvas-width (.-width canvas)
        canvas-height (.-height canvas)
        ctx (.getContext canvas "2d")
        buffered (get-buffered)
        pos (get-playback-position)
        duration (get-episode-duration)
        sec-width (/ canvas-width duration)
        cur-pos-x (* pos sec-width)
        cur-pos-y (/ canvas-height 2)
        cur-pos-r (/ canvas-height 2)]
    (dom/set-html! timeline-position-selector
                   (str (format-pos pos true) " / " (format-pos duration true)))
    (draw-rect! ctx 0 0 canvas-width canvas-height "lightgray")
    (doseq [[start end] buffered
            :let [start-x (* start sec-width)
                  end-x (* end sec-width)
                  width (- end-x start-x)]]
      (draw-rect! ctx start-x 0 width canvas-height color-buffered))
    (draw-rect! ctx 0 0 cur-pos-x canvas-height color-played)
    (.beginPath ctx)
    (.arc ctx cur-pos-x cur-pos-y cur-pos-r
          0 (* js/Math.PI 2) false)
    (set! (.-fillStyle ctx) color-position)
    (.fill ctx)))

(defn set-metadata! [podcast episode]
  (set! (.-metadata js/navigator.mediaSession)
        (js/MediaMetadata. (clj->js {:title (:title episode)
                                     :artist (:artist episode)
                                     :podcast (:title podcast)
                                     :artwork [{:src (:image podcast)}]}))))

(defn timestamp-click-handler [ev]
  (let [ts (js/parseFloat ev.target.dataset.timestamp)
        hash (str "#" ev.target.id)]
    (log "Clicked timestamp in transcript:" ts)
    (js/window.location.replace hash)
    (set-playback-position! ts)))

(declare play-episode!)

(defn seek-to-ts! []
  (when-not (empty? js/window.location.hash)
    (let [ts (-> js/window.location.hash
                 (str/replace-first "#timestamp-" "")
                 (str/replace "-" "."))
          el (js/document.querySelector js/window.location.hash)]
      (log "Jumping to timestamp named in location.hash:" ts)
      (set-playback-position! (js/parseFloat ts))
      (when el
        (let [button (js/document.createElement "button")]
          (.setAttribute button "aria-label" "Play from current timestamp")
          (dom/set-styles! button "margin-right: 5px;")
          (set! (.-title button) "Play from here")
          (set! (.-innerHTML button) "▶️")
          (.addEventListener button "mouseover"
                             #(set! (.-transform (.-style button)) "scale(1.2)"))
          (.addEventListener button "mouseout"
                             #(set! (.-transform (.-style button)) "scale(1.0)"))
          (.addEventListener button "click"
                             (fn [_ev]
                               (set! (.-display (.-style button)) "none")
                               (play-episode!)))
          (.insertBefore (.-parentElement el) button el))
        (js/window.scrollTo (clj->js {:top (.-offsetTop el)}))))))

(defn display-transcript! [{:keys [transcript-selector] :as opts} transcript]
  (log "Displaying transcript:" transcript)
  (dom/set-html! transcript-selector transcript)
  (doseq [span (dom/get-els "#transcript-body" "span.timestamp")
          :let [id (str "timestamp-"
                        (-> span
                            (dom/get-attribute "data-timestamp")
                            (str/replace "." "-")))]]
    (set! (.-id span) id)
    (set! (.-title span) "Jump to here")
    (.addEventListener span "click" timestamp-click-handler))
  (seek-to-ts!))

(defn transcript-id
  ([i]
   (str "transcript-p-" i))
  ([i el-type]
   (str (transcript-id i) "-" (name el-type))))

(defn create-transcript-el [i el-type v]
  (let [span (when v
               (dom/create-el "span"
                              {:id (transcript-id i el-type)
                               :class (str "transcript-" (name el-type))
                               :text v}))]
    (case el-type
      :p (dom/create-el "div" {:id (transcript-id i)
                               :class "transcript-paragraph"})
      :ts (when span
            ;; TODO: set seek listener
            span)
      :speaker (when span
                 (dom/set-text! span (str v ":"))
                 span)
      :text span)))

(defn create-transcript-paragraph [i {:keys [ts speaker text]}]
  (let [p-el (create-transcript-el i :p nil)
        ts-el (create-transcript-el i :ts ts)
        speaker-el (create-transcript-el i :speaker speaker)
        text-el (create-transcript-el i :text text)
        text-wrapper-el (dom/create-el "div" {:class "transcript-text-wrapper"})]
    (when speaker-el
      (dom/add-child! text-wrapper-el speaker-el))
    (dom/add-child! text-wrapper-el text-el)
    (when ts-el
      (dom/add-child! p-el ts-el))
    (dom/add-child! p-el text-wrapper-el)
    p-el))

(defn load-transcript [transcript-url]
  (log "Fetching transcript from:" transcript-url)
  (p/let [transcript-type (-> transcript-url file-extension keyword)
          transcript
          (p/->> (js/fetch (js/Request. transcript-url))
                 (.text)
                 (transcript/parse-transcript transcript-type)
                 (assoc {:transcript-type transcript-type} :transcript))]
    (swap! state assoc :transcript transcript)
    transcript))

(defn display-transcript! [{:keys [transcript-selector] :as opts}
                           {:keys [transcript-type transcript]}]
  (let [el (dom/get-el transcript-selector)
        paragraphs (->> transcript
                        (map-indexed create-transcript-paragraph))]
    (dom/set-children! el paragraphs)
    el))

(defn activate-episode!
  [{:keys [single-episode audio-selector transcript-selector] :as opts}]
  (let [{:keys [podcast active-episode paused?]} @state
        episode-index (get-episode-index @state)
        {:keys [artist title number transcript transcript-url src] :as episode}
        (get-in podcast [:episodes episode-index])]
    (log "Activating episode:" (clj->js episode))
    (when (and transcript-selector transcript-url)
      (when-not transcript
        (p/let [transcript (load-transcript transcript-url)]
          (swap! state update-in [:podcast :episodes episode-index]
                 assoc :transcript transcript)
          (display-transcript! opts transcript))))
    (let [audio-el (:audio-el @state)]
      (dom/set-attribute! audio-el :title (str artist " - " title))
      (when-not single-episode
        (let [episode-spans (dom/get-children "#episodes")]
          (doseq [span episode-spans]
            (dom/set-styles! span "font-weight: normal;"))
          (-> episode-spans
              (nth episode-index)
              (dom/set-styles! "font-weight: bold;"))))
      (dom/set-attribute! audio-el :src src)
      (when-not paused?
        (.play audio-el)))
    (display-timeline! opts)
    (set-metadata! podcast episode)
    episode))

(defn button-selector [button-name]
  (str "#" (name button-name) "-button"))

(defn toggle-button! [button-name src tgt]
  (if-let [button (dom/get-el (button-selector button-name))]
    (doseq [cls ["drop-shadow" "bg" "shine"]
            p (.querySelectorAll button (str "." cls src))]
      (dom/add-class! p (str cls tgt))
      (dom/remove-class! p (str cls src)))
    (log (str "Couldn't resolve button with selector: "
              (button-selector button-name)
              "; button is probably not enabled"))))

(defn turn-off-button! [button-name]
  (toggle-button! button-name "" "-off"))

(defn turn-on-button! [button-name]
  (toggle-button! button-name "-off" ""))

(defn show-button! [button-name]
  (dom/set-styles! (button-selector button-name) "display: inline"))

(defn hide-button! [button-name]
  (dom/set-styles! (button-selector button-name) "display: none"))

(defn toggle-repeat! []
  (let [{:keys [repeating? repeating-all?]} (swap! state toggle-repeat)]
    (cond
      repeating-all?
      (do
        (log "Repeating all")
        (show-button! :repeat)
        (hide-button! :repeat-one)
        (turn-on-button! :repeat))

      repeating?
      (do
        (log "Repeating one")
        (hide-button! :repeat)
        (show-button! :repeat-one)
        (turn-off-button! :repeat))

      :else
      (do
        (log "Repeat off")
        (hide-button! :repeat-one)
        (show-button! :repeat)))))

(defn toggle-shuffle! []
  (let [{:keys [shuffling?]} @state]
    (if shuffling?
      (turn-off-button! :shuffle)
      (turn-on-button! :shuffle))
    (swap! state toggle-shuffle))
  (let [{:keys [shuffling? next-episodes]} @state]
    (log (str "Shuffle " (if shuffling? "on" "off")
              "; playlist:")
         (format-playlist @state))))

(defn advance-episode! []
  (let [{:keys [next-episodes]} @state
        next-episode (first next-episodes)]
    (when next-episode
      (log (str "Advancing to " (format-playback next-episode 0)))
      (swap! state advance-episode)
      (activate-episode!)
      (log "Playlist:" (format-playlist @state)))))

(defn auto-advance-episode! []
  (log (str "Ended " (format-playback)))
  (let [cur-episode (:active-episode @state)
        {:keys [active-episode]} (swap! state auto-advance-episode)]
    (if (= cur-episode active-episode)
      (log "Playlist:" (format-playlist @state))
      (do
        (activate-episode!)
        (log (str "Advanced to episode " active-episode "; playlist:")
             (format-playlist @state))))))

(defn back-episode! []
  (let [{:keys [active-episode prev-episodes]} @state
        prev-episode (first prev-episodes)
        at-start-of-episode? (<= (get-playback-position) 1.0)]
    (if (and at-start-of-episode? prev-episode)
      (do
        (log (str "Moving back to " (format-playback prev-episode 0)))
        (swap! state back-episode)
        (activate-episode!)
        (log "Playlist:" (format-playlist @state)))
      (do
        (log (str "Moving back to " (format-playback active-episode 0)
                  "; playlist:")
             (format-playlist @state))
        (set-playback-position! 0.0)))))

(defn rewind-episode! []
  (let [cur-pos (get-playback-position)
        new-pos (- cur-pos 15.0)
        seekable (get-seekable)
        [start _] (some (fn [[start end :as seekable]]
                          (and (>= cur-pos start) (<= cur-pos end)
                               seekable))
                        seekable)
        new-pos (max 0 start new-pos)]
    (log (str "Rewinding to " (format-playback nil new-pos)))
    (set-playback-position! new-pos)))

(defn fast-forward-episode!
  ([]
   (let [cur-pos (get-playback-position)
         new-pos (+ cur-pos 15.0)]
     (fast-forward-episode! cur-pos new-pos)))
  ([cur-pos new-pos]
   (let [seekable (get-seekable)
         [_ end] (some (fn [[start end :as seekable]]
                         (and (>= cur-pos start) (<= cur-pos end)
                              seekable))
                       seekable)
         end (or end (-> (last seekable) second))
         new-pos (min (get-episode-duration) end new-pos)]
     (log (str "Fast forwarding to " (format-playback nil new-pos)))
     (set-playback-position! new-pos))))

(defn play-episode! []
  (log (str "Playing from " (format-playback)))
  (.play (:audio-el @state))
  (swap! state assoc :paused? false)
  (hide-button! :play)
  (show-button! :pause)
  (turn-on-button! :stop)
  (dom/focus-el (button-selector :pause))
  (log "Playlist:" (format-playlist @state)))

(defn pause-episode! []
  (log (str "Pausing at " (format-playback)))
  (.pause (:audio-el @state))
  (swap! state assoc :paused? true)
  (hide-button! :pause)
  (show-button! :play)
  (dom/focus-el (button-selector :play)))

(defn stop-episode! []
  (log (str "Stopping at " (format-playback)))
  (let [audio (:audio-el @state)]
    (.pause audio)
    (set! (.-currentTime audio) 0.0))
  (swap! state assoc :paused? true)
  (show-button! :play)
  (hide-button! :pause)
  (turn-off-button! :stop)
  (dom/focus-el (button-selector :play)))

(defn move-to-episode! [n]
  (log (str "Moving to episode " n " from " (format-playback)))
  (if (= (:active-episode @state) n)
    (do
      (stop-episode!)
      (play-episode!))
    (do
      (swap! state move-to-episode n)
      (activate-episode!)))
  (log "Playlist:" (format-playlist @state)))

(defn audio-event-handler
  ([]
   (audio-event-handler nil))
  ([handler]
   (fn [ev]
     (let [pos (get-playback-position)
           buffered (get-buffered)
           seekable (get-seekable)]
       (log (str (.-type ev) " at "
                 (format-playback)
                 "; buffered:" (pr-str buffered)
                 "; seekable:" (pr-str seekable))
            (.-target ev))
       (when handler (handler ev))
       true))))

(defn seek-handler [ev]
  (when (or (= "mouseup" (.-type ev))
            (and (= "mouseleave" (.-type ev))
                 (pos? (.-buttons ev))))
    (let [canvas (:timeline-canvas-el @state)
          canvas-width (.-width canvas)
          canvas-height (.-height canvas)
          duration (get-episode-duration)
          sec-width (/ canvas-width duration)
          x (.-offsetX ev)
          pos (/ x sec-width)]
      (log (str "Requesting seek to " (format-playback nil pos)))
      (fast-forward-episode! pos pos))))

(defn add-click-handler! [button-name f]
  (let [button-id (button-selector button-name)
        button (dom/get-el button-id)]
    (log (str "Installing click handler for " button-name)
         button)
    (.addEventListener button "click"
                       (fn [_ev]
                         (log (str (name button-name) " clicked; "
                                   (format-playback)))
                         (f)))))

(defn mk-player-button [button-name]
  (log "Making player button:" button-name)
  (let [button-el (dom/create-el "button" {:id (str (name button-name) "-button")})
        svg-el (mk-svg)
        span-el (dom/create-el "span" {:class "sr-only"})
        {:keys [label paths]} (buttons button-name)]
    (dom/set-html! span-el label)
    (doseq [path paths]
      (dom/add-child! svg-el (apply mk-svg-path path)))
    (dom/add-child! button-el svg-el)
    (dom/add-child! button-el span-el)
    button-el))

(defn init-buttons! [{:keys [controls-selector enabled-buttons] :as opts}]
  (dom/set-children! (dom/get-el controls-selector)
                     (map mk-player-button enabled-buttons))
  (when (:shuffle enabled-buttons)
    (turn-off-button! :shuffle)
    (add-click-handler! :shuffle toggle-shuffle!))
  (when (:repeat enabled-buttons)
    (turn-off-button! :repeat)
    (add-click-handler! :repeat toggle-repeat!))
  (when (:stop enabled-buttons)
    (turn-off-button! :stop)
    (add-click-handler! :stop stop-episode!))
  (when (:pause enabled-buttons)
    (hide-button! :pause)
    (add-click-handler! :pause pause-episode!))
  (when (:repeat-one enabled-buttons)
    (hide-button! :repeat-one)
    (add-click-handler! :repeat-one toggle-repeat!))
  (when (:play enabled-buttons)
    (dom/focus-el (button-selector :play))
    (add-click-handler! :play play-episode!))
  (when (:back enabled-buttons)
    (add-click-handler! :back back-episode!))
  (when (:rewind enabled-buttons)
    (add-click-handler! :rewind rewind-episode!))
  (when (:fast-forward enabled-buttons)
    (add-click-handler! :fast-forward fast-forward-episode!))
  (when (:next enabled-buttons)
    (add-click-handler! :next advance-episode!)))

(defn init-audio! [{:keys [audio-selector timeline-canvas-selector] :as opts}]
  (let [audio (dom/get-el audio-selector)
        canvas (dom/get-el timeline-canvas-selector)]
    (swap! state assoc
           :audio-el audio
           :timeline-canvas-el canvas)
    (.addEventListener audio "ended"
                       (audio-event-handler auto-advance-episode!))
    (.addEventListener audio "durationchange"
                       (audio-event-handler (partial display-timeline! opts)))
    (.addEventListener audio "timeupdate"
                       (audio-event-handler (partial display-timeline! opts)))
    (.addEventListener canvas "mouseup" seek-handler)
    (.addEventListener canvas "mouseleave" seek-handler)
    (log "Audio initialised")))

(defn episode->span [{:keys [number title] :as episode}]
  (let [span (js/document.createElement "span")]
    (set! (.-innerHTML span) (str number ". " title))
    (dom/add-class! span "clickable")
    (.addEventListener span "click" (partial move-to-episode! number))
    span))

(defn display-podcast! [{:keys [main-selector cover-selector
                                title-selector description-selector
                                transcript-selector
                                title-fmt single-episode]
                         :as opts}
                        {:keys [artist title image episodes] :as podcast}]
  (let [cover (dom/get-el cover-selector)
        main (dom/get-el main-selector)
        episode (episodes (get-episode-index @state))
        fmt-data {:podcast podcast, :episode episode}]
    (when cover
      (set! (.-src cover) image))
    (when title-selector
      (dom/set-html! title-selector (fmt title-fmt fmt-data)))
    (if single-episode
      (when description-selector
        (dom/set-html! description-selector (:description episode)))
      (->> episodes
           (map episode->span)
           (dom/set-children! (dom/get-el "#episodes"))))
    (when main
      (dom/set-styles! main "display: flex;"))
    podcast))

(defn load-ui! [{:keys [feed-url] :as opts}]
  (p/let [podcast (load-podcast feed-url)
          {:keys [opts]} (reset! state (init-state opts podcast))]
    (init-audio! opts)
    (display-podcast! opts podcast)
    (init-buttons! opts)
    (activate-episode! opts)
    (swap! state assoc :loaded true)
    (log "UI loaded successfully")))

(set! (.-loadUI js/window) load-ui!)

(when-not (:loaded @state)
  (load-ui! (parse-opts)))
