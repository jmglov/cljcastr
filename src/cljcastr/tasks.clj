(ns cljcastr.tasks
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-server :as http]
            [babashka.process :as p]
            [cljcastr.rss :as rss]
            [cljcastr.template :as template]
            [cljcastr.util :as util :refer [->int ->map]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sci.nrepl.browser-server :as bp]
            [selmer.parser :as selmer]))

(def ^:dynamic *default-podcast-config-filename* "podcast.edn")

(def default-opts
  {:assets-dir "assets"
   :templates-dir "templates"
   :css-file (fs/file "css" "main.css")
   :feed-file "feed.rss"
   :index-file "index.html"
   :http-port 1341
   :http-root "public"
   :nrepl-port 1339
   :websocket-port 1340})

(defn load-edn [filename]
  (-> (slurp filename)
      edn/read-string))

(defn load-config
  ([dir]
   (load-config dir *default-podcast-config-filename*))
  ([dir filename]
   (load-config dir filename {}))
  ([dir filename opts]
   (->> opts
        (merge {:base-dir (str (fs/cwd))}
               (load-edn (fs/file dir filename))
               (cli/parse-opts *command-line-args*)))))

(defn shell [& args]
  (let [p (apply p/shell {:out :string
                          :err :string
                          :continue true}
                 args)]
    (println (:out p))
    (when-not (zero? (:exit p))
      (println (:err p)))
    p))

(defn render-file [filename opts]
  (selmer/render (slurp filename) opts))

(defn render
  ([opts]
   (render (str (fs/cwd)) opts))
  ([dir opts]
   (render dir *default-podcast-config-filename* opts))
  ([dir filename opts]
   (let [{:keys [base-dir assets-dir cljs-dir templates-dir
                 css-file feed-file index-file
                 episodes episodes-dir out-dir] :as opts}
         (load-config dir filename (merge {:base-dir (fs/cwd)}
                                          default-opts
                                          opts))
         cljs-files (->> (fs/list-dir (io/resource "cljs/cljcastr"))
                         (map (fn [filename]
                                (let [src-filename (util/relative-filename
                                                    (fs/file (io/resource "cljs"))
                                                    filename)
                                      tgt-filename (fs/file out-dir cljs-dir src-filename)]
                                  (->map filename src-filename tgt-filename)))))
         opts (if (and (:dev opts) (:http-port opts))
                (assoc opts :base-url
                       (format "http://localhost:%s" (:http-port opts)))
                opts)
         opts (assoc-in opts [:cljcastr-player :files]
                        (map :src-filename cljs-files))
         out-dir (fs/file base-dir out-dir)
         css-out-file (fs/file out-dir css-file)
         css-contents (render-file (fs/file base-dir assets-dir css-file) opts)
         feed-file (fs/file out-dir feed-file)
         index-out-file (fs/file out-dir index-file)
         index-contents (->> (template/expand-context 5 opts)
                             (render-file (fs/file base-dir templates-dir index-file)))
         episode-template (-> (:episode-template opts)
                              (template/expand-template opts))
         page-template (-> (:page-template opts)
                           (template/expand-template opts))
         updated-asset-files (util/copy-modified! assets-dir out-dir)]

     (doseq [file updated-asset-files]
       (println (format "Asset file %s/%s newer than %s/%s; updating"
                        assets-dir file (fs/relativize base-dir out-dir) file)))

     (doseq [{:keys [filename path slug] :as episode}
             (->> (:episodes opts)
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
     (println "Writing RSS feed:"
              (util/relative-filename out-dir feed-file))

     (->> (rss/podcast-feed opts)
          (spit feed-file))

     (doseq [{:keys [filename src-filename tgt-filename]} cljs-files]
       (when (util/modified-since? filename tgt-filename)
         (println (format "cljs file %s modified; updating" src-filename))
         (fs/create-dirs (fs/parent tgt-filename))
         (fs/copy filename tgt-filename {:replace-existing true})))

     (let [template (slurp episode-template)
           opts (rss/update-episodes opts)]
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
           (spit tgt-filename content)))))))

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
        paths (->> (apply shell (concat sync-cmd ["--dryrun"]))
                   :out
                   str/split-lines
                   (map #(str/replace % paths-re "$1"))
                   (filter not-empty))
        invalidate-cmd (concat ["aws cloudfront create-invalidation"
                                "--distribution-id" distribution-id
                                "--paths"]
                               paths)]
    (apply println sync-cmd)
    (apply shell sync-cmd)
    (if (empty? paths)
      (println "Skipping invalidation because nothing was synced")
      (do
        (apply println invalidate-cmd)
        (when-not dryrun?
          (apply shell invalidate-cmd))))))

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
