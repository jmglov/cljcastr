(ns cljcastr.util)

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))
