(ns cljcastr.tasks
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-server :as http]
            [babashka.process :as p]
            [cljcastr.audio :as audio]
            [cljcastr.rss :as rss]
            [cljcastr.template :as template]
            [cljcastr.transcription :as transcription]
            [cljcastr.transcription.otr :as otr]
            [cljcastr.transcription.zencastr :as zencastr]
            [cljcastr.util :as util :refer [->int ->map]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sci.nrepl.browser-server :as bp]
            [selmer.parser :as selmer]))

(def ^:dynamic *default-podcast-config-filename* "podcast.edn")

(def default-opts
  {:assets-dir "assets"
   :cljs-dir "cljs"
   :css-dir "css"
   :image-dir "img"
   :templates-dir "templates"
   :css-file (fs/file "css" "main.css")
   :feed-file "feed.rss"
   :index-file "index.html"
   :transcribe-file "transcribe.html"
   :transcribe-template (io/resource "transcribe.html")
   :http-port 1341
   :http-root "public"
   :nrepl-port 1339
   :websocket-port 1340
   :processing true
   :fixup-timestamps false
   :remove-timestamps false
   :remove-fillers true
   :remove-active-listening true
   :remove-repeated-words true
   :join-speakers true})

(def cljs-src-dir (io/resource "cljs"))
(def css-src-dir (io/resource "css"))
(def image-src-dir (io/resource "img"))
(def favicon-src-dir (fs/file (fs/cwd) "favicon"))

(def ->cljs-src-filenames (partial map (partial fs/file "cljcastr")))

(def cljs-libs-spec
  {:src-dir cljs-src-dir
   :src-filenames (->cljs-src-filenames ["dom.cljs" "time.cljc"])})

(def cljs-app-spec
  {:player {:src-dir cljs-src-dir
            :src-filenames (->cljs-src-filenames ["player.cljs"])}
   :transcribe {:src-dir cljs-src-dir
                :src-filenames (->cljs-src-filenames ["transcribe.cljs"])}})

(defn load-edn [filename]
  (-> (slurp filename)
      edn/read-string))

(defn load-config
  ([dir]
   (load-config dir *default-podcast-config-filename*))
  ([dir filename]
   (load-config dir filename {}))
  ([dir filename opts]
   (let [cli-args (cli/parse-args *command-line-args*)
         cli-opts (:opts cli-args)
         merged-opts (merge {:base-dir (str (fs/cwd))}
                            (load-edn (fs/file dir filename))
                            opts
                            cli-opts
                            {::args (:args cli-args)})]
     (util/debug merged-opts (format "Command line args: %s"
                                     (str/join " " *command-line-args*)))
     (util/debug merged-opts (format "Parsed opts: %s" (pr-str cli-opts)))
     merged-opts)))

(defn copy-files
  "Given a `dirs-spec` of `{FILE_TYPE_1
                            {:src-dir SRC_DIR_1, :tgt-dir TGT_DIR_1,
                             :src-filenames [FILENAME_1, FILENAME2, ...]}
                            FILE_TYPE_2
                            {...}
                            ...}`
   copies `SRC_DIR_1/FILENAME_1` to `TGT_DIR_1/FILENAME_1`,
   `SRC_DIR_1/FILENAME_2` to `TGT_DIR_1/FILENAME_2`, `SRC_DIR2/FILENAME_3` to
   `TGT_DIR_2/FILENAME_3`, ... and returns a map of
   `{FILE_TYPE_1 [{:filename `SRC_DIR_1/FILENAME_1`
                   :src-filename `FILENAME_1`
                   :tgt-filename `TGT_DIR_1/FILENAME_1`}
                  ...]
     FILE_TYPE_2 [...]
     ...}`"
  ([opts dirs-spec]
   (copy-files (str (fs/cwd)) opts dirs-spec))
  ([dir opts dirs-spec]
   (copy-files dir *default-podcast-config-filename* opts dirs-spec))
  ([dir filename opts dirs-spec]
   (let [{:keys [out-dir] :as opts}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         files
         (->> dirs-spec
              (map (fn [[file-type {:keys [src-filenames src-dir tgt-dir] :as spec}]]
                     [file-type
                      (->> src-filenames
                           (map (fn [src-filename]
                                  (let [filename (fs/file src-dir src-filename)
                                        tgt-filename (fs/file out-dir tgt-dir src-filename)]
                                    (->map filename src-filename tgt-filename)))))]))
              (into {}))]
     (doseq [[file-type file-list] files
             {:keys [filename src-filename tgt-filename]} file-list]
       (when (util/modified-since? filename tgt-filename)
         (println (format "%s file %s modified; updating"
                          (name file-type) src-filename))
         (fs/create-dirs (fs/parent tgt-filename))
         (fs/copy filename tgt-filename {:replace-existing true})))
     files)))

(defn make-dir-spec
  "Returns a dir-spec (see `copy-files`) for all of the files in `src-dir`."
  [src-dir tgt-dir]
  (let [src-filenames (map (partial util/relative-filename src-dir)
                           (fs/list-dir src-dir))]
    (->map src-filenames src-dir tgt-dir)))

(defn render-file [filename opts]
  (selmer/render (slurp filename) opts))

(defn render
  ([opts]
   (render (str (fs/cwd)) opts))
  ([dir opts]
   (render dir *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [base-dir assets-dir cljs-dir css-dir image-dir templates-dir
                 dirs-spec
                 css-file feed-file index-file transcribe-file
                 episodes episodes-dir out-dir] :as opts}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         files (copy-files dir filename opts
                           (merge
                            {:player (assoc (cljs-app-spec :player)
                                            :tgt-dir (:cljs-dir opts))
                             :transcribe (assoc (cljs-app-spec :transcribe)
                                                :tgt-dir cljs-dir)
                             :cljs-lib (assoc cljs-libs-spec
                                              :tgt-dir (:cljs-dir opts))
                             :css (make-dir-spec css-src-dir
                                                 (:css-dir opts))
                             :image (make-dir-spec image-src-dir
                                                   (:image-dir opts))}
                            dirs-spec))
         opts (if (and (:dev opts) (:http-port opts))
                (assoc opts :base-url
                       (format "http://localhost:%s" (:http-port opts)))
                opts)
         episode-opts (assoc-in opts [:cljcastr-player :files]
                                (concat (map :src-filename (:cljs-lib files))
                                        (map :src-filename (:player files))))
         transcribe-opts (assoc-in opts [:cljcastr-transcribe :files]
                                   (concat (map :src-filename (:cljs-lib files))
                                           (map :src-filename (:transcribe files))))
         out-dir (fs/file base-dir out-dir)
         css-out-file (fs/file out-dir css-file)
         css-contents (render-file (fs/file base-dir assets-dir css-file) opts)
         feed-file (fs/file out-dir feed-file)
         index-out-file (fs/file out-dir index-file)
         index-contents (->> (template/expand-context 5 opts)
                             (render-file (fs/file base-dir templates-dir index-file)))
         episode-template (-> (:episode-template opts)
                              (template/expand-template episode-opts))
         page-template (when-let [template (:page-template opts)]
                         (template/expand-template template opts))
         transcribe-out-file (fs/file out-dir transcribe-file)
         transcribe-contents (-> (:transcribe-template opts)
                                 (template/expand-template transcribe-opts)
                                 slurp
                                 (selmer/render transcribe-opts))
         updated-asset-files (util/copy-modified! assets-dir out-dir)]

     (doseq [file updated-asset-files]
       (println (format "Asset file %s/%s newer than %s/%s; updating"
                        assets-dir file (fs/relativize base-dir out-dir) file)))

     (doseq [{:keys [filename path slug] :as episode}
             (->> (:episodes opts)
                  (filter #(or (:include-previews opts)
                               (not (:preview? %))))
                  (map #(template/expand-context 5 % opts)))
             filename (map episode [:audio-file :transcript-file])
             :let [src-filename (fs/file base-dir slug filename)
                   tgt-filename (fs/file out-dir path filename)]]
       (when-not (fs/exists? tgt-filename)
         (println "Creating episode file:"
                  (util/relative-filename out-dir tgt-filename))
         (fs/create-dirs (fs/parent tgt-filename))
         (fs/copy src-filename tgt-filename))

       (when (util/modified-since? src-filename tgt-filename)
         (println (format "Episode file %s newer than %s; updating"
                          (util/relative-filename base-dir src-filename)
                          (util/relative-filename out-dir tgt-filename)))
         (fs/copy src-filename tgt-filename {:replace-existing true})))

     (when-not (and (fs/exists? index-out-file)
                    (= index-contents (slurp index-out-file)))
       (println (format "Writing %s" (util/relative-filename out-dir index-out-file)))
       (fs/create-dirs (fs/parent index-out-file))
       (spit index-out-file index-contents))

     (when-not (and (fs/exists? css-out-file)
                    (= css-contents (slurp css-out-file)))
       (println (format "Writing %s" (util/relative-filename out-dir css-out-file)))
       (fs/create-dirs (fs/parent css-out-file))
       (spit css-out-file css-contents))

     (let [feed-contents (rss/podcast-feed opts)]
       (when-not (and (fs/exists? feed-file)
                      (= feed-contents (slurp feed-file)))
         (println "Writing RSS feed:"
                  (util/relative-filename out-dir feed-file))
         (spit feed-file feed-contents)))

     (let [template (slurp episode-template)
           opts (rss/update-episodes (assoc episode-opts :bonus-numbers? true))]
       (doseq [{:keys [path] :as episode} (:episodes opts)
               :let [filename (fs/file out-dir path "index.html")
                     opts (assoc opts :episode episode)
                     opts (update-in opts [:cljcastr-player :opts]
                                     #(-> (template/expand-context 5 % opts)
                                          util/->snake_case))
                     content (selmer/render template opts)]]
         (when-not (and (fs/exists? filename)
                        (= content (slurp filename)))
           (println "Writing episode index:"
                    (util/relative-filename out-dir filename))
           (spit filename content))))

     (when (and (:pages opts) page-template)
       (let [template (slurp page-template)]
         (doseq [{:keys [filename] :as page} (:pages opts)
                 :let [ctx (-> (template/expand-context 5 page opts)
                               (update :stylesheets
                                       #(concat (:stylesheets opts) %)))
                       tgt-filename (fs/file out-dir filename)
                       content (selmer/render template (merge opts ctx))]]
           (when-not (and (fs/exists? tgt-filename)
                          (= content (slurp tgt-filename)))
             (println "Writing page:" filename)
             (spit tgt-filename content)))))

     (when-not (and (fs/exists? transcribe-out-file)
                    (= transcribe-contents (slurp transcribe-out-file)))
       (println (format "Writing transcript editor: %s"
                        (util/relative-filename out-dir transcribe-out-file)))
       (fs/create-dirs (fs/parent transcribe-out-file))
       (spit transcribe-out-file transcribe-contents)))))

(defn publish-aws [opts]
  (let [{:keys [website-bucket out-dir distribution-id]
         :as opts} (merge opts
                          (cli/parse-opts *command-line-args*))
        dryrun? (or (:dryrun opts) (:dry-run opts))
        sync-cmd (concat ["aws s3 sync"]
                         (when dryrun? ["--dryrun"])
                         [(format "%s/" out-dir)
                          (format "s3://%s/" website-bucket)])
        paths-re (re-pattern (format "^[(]dryrun[)] upload: %s(/\\S+) to .+$"
                                     out-dir))
        paths (->> (apply util/shell (concat sync-cmd ["--dryrun"]))
                   :out
                   str/split-lines
                   (map #(str/replace % paths-re "$1"))
                   (filter not-empty))
        invalidate-cmd (concat ["aws cloudfront create-invalidation"
                                "--distribution-id" distribution-id
                                "--paths"]
                               paths)]
    (apply println sync-cmd)
    (apply util/shell sync-cmd)
    (if (empty? paths)
      (println "Skipping invalidation because nothing was synced")
      (do
        (apply println invalidate-cmd)
        (when-not dryrun?
          (apply util/shell invalidate-cmd))))))

(defn http-server [opts]
  (let [{http-port :http-port, http-root :http-root}
        (merge default-opts opts (cli/parse-opts *command-line-args*))
        http-port (->int http-port)]
    (println (format "Starting webserver on port %d with root %s"
                     http-port http-root))
    (try
      (http/serve {:port http-port, :dir http-root})
      ;; Annoyingly, this prints even when an exception is thrown
      (println (format "Serving static assets at http://localhost:%d" http-port))
      (catch java.net.BindException e
        (println (format "Address in use: localhost:%d" http-port))
        (throw e)))))

(defn browser-nrepl [opts]
  (let [{:keys [nrepl-port websocket-port]}
        (merge default-opts opts (cli/parse-opts *command-line-args*))
        nrepl-port (->int nrepl-port)
        websocket-port (->int websocket-port)]
    (println (format "Starting nrepl server on port %d and websocket server on port %d"
                     nrepl-port websocket-port))
    (bp/start! (->map nrepl-port websocket-port))))

(defn fixup-transcript
  ([opts]
   (fixup-transcript (fs/cwd) opts))
  ([dir opts]
   (fixup-transcript dir *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [infile outfile backup-file
                 processing
                 fixup-timestamps remove-timestamps
                 remove-fillers remove-repeated-words
                 remove-active-listening join-speakers
                 offset-ts start-at-ts] :as opts}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         outfile (if outfile outfile infile)
         offset-ts (or offset-ts (:episode-start-offset-ts opts))
         opts (assoc opts :outfile outfile, :offset-ts offset-ts)
         backup-file (or backup-file (format "%s.BAK" outfile))
         parse-fn (transcription/parse-fn (str/replace infile #"[.]BAK$" ""))
         generate-fn (transcription/generate-fn (str/replace outfile #"[.]BAK$" ""))
         paragraphs (-> infile slurp parse-fn)
         transform-fn (apply comp
                             (if processing
                               (->> [(when fixup-timestamps
                                       transcription/fixup-timestamps)
                                     (when remove-fillers
                                       transcription/remove-fillers)
                                     (when remove-repeated-words
                                       transcription/remove-repeated-words)
                                     (when remove-active-listening
                                       transcription/remove-active-listening)
                                     (when join-speakers
                                       transcription/join-speakers)
                                     (when remove-timestamps
                                       transcription/remove-timestamps)]
                                    (remove nil?))
                               [(fn [_opts output] output)]))
         _ (println (format "Reading file %s" infile))
         transcript (->> paragraphs
                         (transform-fn opts)
                         (generate-fn opts))]
     (when (fs/exists? outfile)
       (println (format "Writing backup file %s" backup-file))
       (fs/copy outfile backup-file {:replace-existing true}))
     (println (format "Writing file %s" outfile))
     (spit outfile transcript))))

(defn diff-transcripts
  ([opts]
   (diff-transcripts (fs/cwd) opts))
  ([dir opts]
   (diff-transcripts (fs/cwd) *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [::args] :as opts}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         [a-file b-file] args
         a-type (transcription/transcript-type a-file)
         b-type (transcription/transcript-type b-file)
         _ (when-not (= a-type b-type)
             (throw (ex-info "Cannot diff transcripts with different types"
                             {::a-type a-type, ::b-type b-type})))
         parse-fn (transcription/parse-fn a-file)
         generate-fn (transcription/generate-fn a-file)
         generate-opts (merge opts
                              (case a-type
                                :otr {:otr-html-only true}
                                {}))
         a-paragraphs (->> (slurp a-file)
                           parse-fn)
         b-paragraphs (->> (slurp b-file)
                           parse-fn)]
     (println (format "diff -u %s %s" a-file b-file))
     (fs/with-temp-dir [dir]
       (let [a (fs/file dir (fs/file-name a-file))
             b (fs/file dir (fs/file-name b-file))]
         (spit a (generate-fn generate-opts a-paragraphs))
         (spit b (generate-fn generate-opts b-paragraphs))
         (util/shell "diff -u" a b))))))

(defn list-episode-audio-metadata
  ([opts]
   (list-episode-audio-metadata (fs/cwd) opts))
  ([dir opts]
   (list-episode-audio-metadata (fs/cwd) *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [episodes]}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))]
     (doseq [episode episodes]
       (audio/list-id3-info opts (template/expand-context 5 episode opts))))))

(defn write-episode-audio-metadata
  ([opts]
   (write-episode-audio-metadata (fs/cwd) opts))
  ([dir opts]
   (write-episode-audio-metadata dir *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [seasons episodes]}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         season-number (:season opts)
         season (some #(= season-number (str (:number %))) seasons)]
     (doseq [episode episodes]
       (audio/set-id3-info opts season (template/expand-context 5 episode opts))))))
