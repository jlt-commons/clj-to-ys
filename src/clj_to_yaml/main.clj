(ns clj-to-yaml.main
  "CLI: clj-to-yaml [--string EXPR | FILE]; stdin when no args.
  Prints YS to stdout and returns it."
  (:require [clj-to-yaml.core :as c]
            [clojure.string :as str]))

(defn -main
  ([] (-main nil nil))
  ([& args]
   (let [[flag value] args
         source (cond
                  (= "--string" flag) value
                  (and flag (not (.startsWith flag "--"))) (slurp flag)
                  :else (slurp *in*))
         ys (c/translate source)]
     (print ys)
     ys)))
