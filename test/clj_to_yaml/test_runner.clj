(ns clj-to-yaml.test-runner
  "Hand-rolled test runner. `jolt -M:test` requires every listed test
  namespace and exits non-zero on any failure."
  (:require [clojure.test :as t]
            clj-to-yaml.grammar-test
            clj-to-yaml.render-test
            clj-to-yaml.core-test
            clj-to-yaml.main-test))

(def test-namespaces
  '[clj-to-yaml.grammar-test
    clj-to-yaml.render-test
    clj-to-yaml.core-test
    clj-to-yaml.main-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
    (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1))))
