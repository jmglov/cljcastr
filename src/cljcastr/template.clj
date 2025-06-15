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

(comment

  (has-template-var? "{{foo}}")
  ;; => "{{foo}}"

  (has-template-var? "foo")
  ;; => nil

  (has-template-var? {:a ["foo" "bar"]
                      :b {:c [1 2 3]}
                      :d {:e "{{f}}"}})
  ;; => ({:e "{{f}}"})

  (has-template-var? {:a ["foo" "bar"]
                      :b {:c [1 2 3]}
                      :d {:e "f"}})
  ;; => nil

  )

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
                 [k (expand-template v ctx)]))
          (into {}))

     ;; If template is none of the above, just return it as is
     :else
     template)))

(comment

  (expand-template "foo"
                   {:foo "stuff", :bar "things"})
  ;; => "foo"

  (expand-template "{{foo}}"
                   {:foo "stuff", :bar "things"})
  ;; => "stuff"

  (expand-template ["foo" "bar"]
                   {:foo "stuff", :bar "things"})
  ;; => ("foo" "bar")

  (expand-template ["{{foo}}" "{{bar}}"]
                   {:foo "stuff", :bar "things"})
  ;; => ("stuff" "things")

  ;; => "foo"

  (expand-template {:a "foo", :b "bar"}
                   {:foo "stuff", :bar "things"})
  ;; => {:a "foo", :b "bar"}

  (expand-template {:a "{{foo}}", :b "{{bar}}"}
                   {:foo "stuff", :bar "things"})
  ;; => {:a "stuff", :b "things"}

  (expand-template {:a ["{{foo}}" "two"]
                    :b {:c "{{bar}}"
                        :d "e"}}
                   {:foo "stuff", :bar "things"})
  ;; => {:a ("stuff" "two"), :b {:c "things", :d "e"}}

  )

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

(comment

  (expand-context {:a "{{b}}"})
  ;; => {:a "{{b}}"}

  (expand-context {:a "{{b}}"
                   :b "{{c}}"})
  ;; => {:a "{{c}}", :b "{{c}}"}

  (expand-context {:a "{{b}}"
                   :b "{{c}}"
                   :c "d"})
  ;; => {:a "d", :b "d", :c "d"}

  (expand-context {:a ["{{b}}" "b"]
                   :b "{{c}}"
                   :c "d"})
  ;; => {:a ("d" "b"), :b "d", :c "d"}

  (expand-context {:a "same"
                   :b "{{a}}"
                   :c ["same"
                       "{{b}}"
                       {:d "{{a}}"}]})
  ;; => {:a "same", :b "same", :c ("same" "same" {:d "same"})}

  (expand-context 1 {:a "same"
                     :b "{{a}}"
                     :c ["same"
                         "{{b}}"
                         {:d "{{a}}"}]})
  ;; => {:a "same", :b "same", :c ("same" "{{a}}" {:d "same"})}

  (expand-context 5
                  {:a "{{b}}"
                   :b "from ctx"
                   :c "{{d}}"}
                  {:d "from opts"})
  ;; => {:a "from ctx", :b "from ctx", :c "from opts"}

  )
