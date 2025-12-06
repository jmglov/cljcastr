;; To start a REPL:
;;
;; bb dev
;;
;; Then connect to it in Emacs:
;;
;; C-c l C (cider-connect-cljs), host: localhost; port: 1339; REPL type: nbb

(ns cljcastr.transcript
  (:require [cljcastr.dom :as dom]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]))

(defonce state (atom {}))

(defn log [& args]
  (when (.-CLJCASTR_DEBUG js/window)
    (apply js/console.log args)))

(defn clear-storage! []
  (let [storage (.-localStorage js/window)]
    (log "Clearing" (.-length storage) "keys from local storage")
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

(defn create-transcript-p [i paragraph]
  (let [p (dom/create-el "p" {:id (str "transcript-p-" i)})
        children
        (->> [:ts :speaker :text]
             (map (fn [k] (create-transcript-span i [k (get paragraph k)])))
             (remove nil?))]
    (doseq [child children]
      (.appendChild p child))
    p))

(defn transcript->elements [transcript]
  (map-indexed create-transcript-p transcript))

(defn load-key [k]
  (-> (.-localStorage js/window)
      (.getItem k)))

(defn save-key! [k v]
  (-> (.-localStorage js/window)
      (.setItem k v)))

(defn save-paragraph! [p-el]
  (doseq [span-el (.-childNodes p-el)
          :let [k (.-id span-el)
                v (.-textContent span-el)]]
    (log "Saving key" k "to local storage:" v)
    (save-key! k v)))

(defn display-transcript! [target-el transcript]
  (doseq [p (transcript->elements transcript)]
    (.appendChild target-el p)))

(defn import-file! [target-el event]
  (let [contents (-> event .-target .-result)
        transcript (read-transcript contents)]
    (log "Loaded file:" contents)
    (swap! state assoc :transcript transcript)
    (display-transcript! target-el transcript)
    (clear-storage!)
    (let [children (.-childNodes target-el)]
      (doseq [p children]
        (save-paragraph! p))
      (save-key! "transcript-num-paragraphs" (.-length children)))))

(defn read-file! [target-el event]
  (log "File selected:" event)
  (let [file (-> event .-target .-files first)
        reader (js/FileReader.)]
    (set! (.-onload reader) (partial import-file! target-el))
    (.readAsText reader file)))

(defn handle-input! [ev]
  (let [sel (.getSelection js/window)
        el (.-anchorNode sel)
        parent (.-parentNode el)]
    (log "Input event:" ev)
    (log "Element:" el)
    (log "Parent:" parent)
    (log "Parent ID:" (.-id parent))
    (log "Offset:" (.-focusOffset sel))
    (log "Text:" (.-textContent el))
    (log "Text before:" (subs (.-textContent el) 0 (.-focusOffset sel)))
    (log "Text after:" (subs (.-textContent el) (.-focusOffset sel)))
    (if (= "insertParagraph" (.-inputType ev))
      (log "Insert paragraph here")
      (log "Update text here"))))

(defn load-paragraph [i]
  (log "Loading paragraph" i "from local storage")
  {:ts (load-key (str "transcript-p-" i "-ts"))
   :speaker (load-key (str "transcript-p-" i "-speaker"))
   :text (load-key (str "transcript-p-" i "-text"))})

(defn restore-transcript! [target-el]
  (let [num-paragraphs (load-key "transcript-num-paragraphs")]
    (when num-paragraphs
      (log "Loading transcript from local storage; restoring"
           num-paragraphs "paragraphs")
      (->> (range num-paragraphs)
           (map load-paragraph)
           (display-transcript! target-el)))))

(defn init-ui! []
  (let [transcript-el (dom/get-el "#textbox")]
    (dom/clear-listeners! state)
    (dom/add-listener! state "#import" "change"
                       (partial read-file! transcript-el))
    (dom/add-listener! state "#textbox" "input"
                       handle-input!)
    (restore-transcript! transcript-el)))

(comment

  (init-ui!)

  )
