(ns clj-to-ys.grammar-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-to-ys.grammar :as g]))

(deftest parses-a-simple-call
  (is (= [[:list [:sym "println"] [:str "Hello, World!"]]]
         (g/parse-forms "(println \"Hello, World!\")"))))

(deftest parses-literals
  (is (= [[:num "42"] [:num "-7"] [:num "3.14"] [:num "0xff"] [:num "1/2"]
           [:kw ":foo"] [:kw ":foo/bar"] [:kw "::foo"] [:bool "true"] [:bool "false"] [:nil "nil"]]
         (g/parse-forms "42 -7 3.14 0xff 1/2 :foo :foo/bar ::foo true false nil"))))

(deftest parses-collections
  (is (= [[:list [:sym "f"] [:num "1"]]
          [:vec [:num "1"] [:num "2"]]
          [:map [:kw ":a"] [:num "1"]]
          [:set [:num "1"]]]
         (g/parse-forms "(f 1) [1 2] {:a 1} #{1}"))))

(deftest parses-string-escapes-verbatim
  (is (= [[:str "a\\nb"]] (g/parse-forms "\"a\\nb\""))))

(deftest parses-regex-literal
  (is (= [[:regex "a\\db"]] (g/parse-forms "#\"a\\db\""))))

(deftest skips-whitespace-commas-and-comments
  (is (= [[:sym "a"] [:sym "b"]]
         (g/parse-forms "a, ; trailing\n  b"))))

(deftest parses-reader-shorthand
  (is (= [[:list [:sym "quote"] [:vec [:num "1"]]]
          [:deref [:sym "a"]]
          [:list [:sym "var"] [:sym "f"]]
          [:syntax-quote [:sym "x"]]
          [:unquote [:sym "x"]]
          [:unquote-splicing [:sym "xs"]]]
         (g/parse-forms "'[1] @a #'f `x ~x ~@xs"))))

(deftest meta-and-discard-are-transparent
  (is (= [[:sym "x"] [:sym "y"]]
         (g/parse-forms "^:private x #_ z y"))))

(deftest parse-failure-is-informative
  (is (thrown-with-msg? Exception #"Parse error"
                        (g/parse-forms "(unclosed"))))
