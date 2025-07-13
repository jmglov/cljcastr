(ns cljcastr.template
  (:require [clojure.string :as str]
            [selmer.parser :as selmer]
            [selmer.util]))

(defn has-template-var?
  "Returns truthy if any leaf node contains a Selmer template var."
  [v]
  (cond
    (string? v) (re-seq #"[{][{][^}]+[}][}]" v)
    (sequential? v) (not-empty (filter has-template-var? v))
    (map? v) (has-template-var? (vals v))
    :else false))

(defn expand-template
  "Recursively renders Selmer templates in keys of a context map."
  ([ctx]
   (expand-template ctx ctx))
  ([template ctx]
   (cond
     (string? template)
     (selmer/render template ctx)

     (sequential? template)
     (map #(expand-template % ctx) template)

     (map? template)
     (->> template
          (map (fn [[k v]]
                 (cond
                   (and (keyword? k)
                        (= "cljcastr.template" (namespace k)))
                   [(-> k name keyword)
                    (-> v
                        (expand-template ctx)
                        slurp
                        (expand-template ctx))]

                   :else
                   [k (expand-template v ctx)])))
          (into {}))

     ;; If template is none of the above, just return it as is
     :else
     template)))

(defn expand-context
  "Renders Selmer templates in values of a context map until everything is
   expanded or max-depth is reached."
  ([ctx]
   (expand-context 5 ctx))
  ([max-depth ctx]
   (expand-context max-depth ctx {}))
  ([max-depth ctx opts]
   (selmer.util/set-missing-value-formatter!
    (fn [{:keys [tag-name tag-value] :as tag} _]
      (format "{{%s}}" (or tag-name tag-value))))
   (loop [i 0
          ctx ctx]
     (if (and (< i max-depth) (has-template-var? ctx))
       (recur (inc i) (expand-template ctx (merge opts ctx)))
       (do
         (selmer.util/set-missing-value-formatter! (constantly ""))
         ctx)))))
