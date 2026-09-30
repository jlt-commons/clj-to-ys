(ns clj-to-ys.render-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-to-ys.render :as r]))

(deftest scalars
  (is (= "println" (r/expr [:sym "println"])))
  (is (= "42" (r/expr [:num "42"])))
  (is (= "-7" (r/expr [:num "-7"])))
  (is (= "3.14" (r/expr [:num "3.14"])))
  (is (= "true" (r/expr [:bool "true"])))
  (is (= "nil" (r/expr [:nil "nil"])))
  (is (= ":foo" (r/expr [:kw ":foo"])))
  (is (= ":foo/bar" (r/expr [:kw ":foo/bar"])))
  (is (= "::foo" (r/expr [:kw "::foo"]))))

(deftest strings-prefer-single-quotes
  (is (= "'Hello, World!'" (r/expr [:str "Hello, World!"])))
  (is (= "'a\\nb'" (r/expr [:str "a\\nb"])))
  (is (= "\"it's\"" (r/expr [:str "it's"]))))

(deftest plain-scalar-escaping
  ;; code mode forbids flow collections / YAML indicators as plain scalars
  (is (= "(f 1)" (r/expr [:list [:sym "f"] [:num "1"]]))) ; ( is safe in YAML
  (is (= "+[1 2]" (r/expr [:vec [:num "1"] [:num "2"]])))
  (is (= "+{:a 1}" (r/expr [:map [:kw ":a"] [:num "1"]])))
  (is (= "+#{1}" (r/expr [:set [:num "1"]])))
  (is (= "+#\"a\\db\"" (r/expr [:regex "a\\db"]))))

(deftest reader-shorthand-reconstruction
  (is (= "(quote [1])" (r/expr [:list [:sym "quote"] [:vec [:num "1"]]])))
  (is (= "(deref a)" (r/expr [:deref [:sym "a"]])))
  (is (= "`x" (r/raw [:syntax-quote [:sym "x"]])))
  (is (= "+`x" (r/expr [:syntax-quote [:sym "x"]])))
  (is (= "~x" (r/expr [:unquote [:sym "x"]])))
  (is (= "~@xs" (r/expr [:unquote-splicing [:sym "xs"]]))))
