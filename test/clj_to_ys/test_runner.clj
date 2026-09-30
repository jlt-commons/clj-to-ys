(ns clj-to-ys.test-runner
  "Hand-rolled test runner. `jolt -M:test` requires every listed test
  namespace and exits non-zero on any failure."
  (:require [clojure.test :as t]
            clj-to-ys.grammar-test
            clj-to-ys.render-test
            clj-to-ys.core-test
            clj-to-ys.main-test))

(def test-namespaces
  '[clj-to-ys.grammar-test
    clj-to-ys.render-test
    clj-to-ys.core-test
    clj-to-ys.main-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
    (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1))))
