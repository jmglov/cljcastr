(ns cljcastr.tasks
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cljcastr.rss :as rss]
            [cljcastr.template :as template]
            [cljcastr.util :as util]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [selmer.parser :as selmer]))

(def ^:dynamic *default-podcast-config-filename* "podcast.edn")

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

(defn render
  ([default-opts]
   (render (str (fs/cwd)) default-opts))
  ([dir default-opts]
   (render dir *default-podcast-config-filename* default-opts))
  ([dir filename default-opts]
   (let [{:keys [base-dir episodes episodes-dir out-dir] :as opts}
         (load-config dir filename
                           (merge {:base-dir (fs/cwd)} default-opts))
         out-dir (fs/file base-dir out-dir)
         css-file (fs/file out-dir "css" "main.css")
         css-contents (selmer/render (slurp (fs/file base-dir "assets/css/main.css"))
                                     opts)
         feed-file (fs/file out-dir (or (:feed-file opts) "feed.rss"))
         index-file (fs/file out-dir "index.html")
         index-contents (selmer/render (slurp (fs/file base-dir "templates/index.html"))
                                       opts)]
     (doseq [{:keys [filename path slug] :as episode}
             (->> (load-edn (fs/file dir filename))
                  :episodes
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
         (println "Episode file" (util/relative-filename base-dir src-filename)
                  "newer than" (util/relative-filename out-dir tgt-filename)
                  "; updating")
         (fs/copy src-filename tgt-filename {:replace-existing true})))
     (when-not (and (fs/exists? index-file)
                    (= index-contents (slurp index-file)))
       (println (format "Writing %s" (util/relative-filename out-dir index-file)))
       (fs/create-dirs (fs/parent index-file))
       (spit index-file index-contents))
     (when-not (and (fs/exists? css-file)
                    (= css-contents (slurp css-file)))
       (println (format "Writing %s" (util/relative-filename out-dir css-file)))
       (fs/create-dirs (fs/parent css-file))
       (spit css-file css-contents))
     (println (format "Writing RSS feed %s"
                      (util/relative-filename out-dir feed-file)))
     (->> (rss/podcast-feed opts)
          (spit feed-file))
     #_(let [template (slurp "templates/episode-page.html")
             opts (rss/update-episodes opts)]
         (doseq [{:keys [path] :as episode} (:episodes opts)
                 :let [filename (format "%s/%s%s/%s"
                                        base-dir out-dir path "index.html")]]
           (println "Writing episode page" filename)
           (->> (selmer/render template (assoc opts :episode episode))
                (spit filename)))))))

(defn publish-aws [default-opts]
  (let [{:keys [website-bucket out-dir distribution-id dryrun]
         :as opts} (merge default-opts
                          (cli/parse-opts *command-line-args*))
        sync-cmd (concat ["aws s3 sync"]
                         (when dryrun ["--dryrun"])
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
        (when-not dryrun
          (apply shell invalidate-cmd))))))
