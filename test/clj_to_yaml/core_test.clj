(ns clj-to-yaml.core-test
  "Golden tests. Expected outputs follow the translations documented in the
  official YS 'From Clojure to YS' tutorial and cheat sheet."
  (:require [clojure.test :refer [deftest is testing]]
            [clj-to-yaml.core :as c]))

(deftest hello-world
  (is (= "!ys-0\nsay: 'Hello, World!'\n"
         (c/translate "(println \"Hello, World!\")"))))

(deftest let-becomes-assignments
  (is (= "!ys-0\nx =: 1\ny =: x.inc()\nsay: y\n"
         (c/translate "(let [x 1 y (inc x)] (println y))"))))

(deftest defn-single-arity
  (is (= "!ys-0\ndefn greet(name):\n  say: \"Hello, $name!\"\n"
         (c/translate "(defn greet [name] (println (str \"Hello, \" name \"!\")))"))))

(deftest defn-default-arg
  (is (= "!ys-0\ndefn hello(name='World'):\n  say: \"Hello, $name!\"\n"
         (c/translate "(defn hello ([] (hello \"World\")) ([name] (println (str \"Hello, \" name \"!\"))))"))))

(deftest def-becomes-assignment
  (is (= "!ys-0\nanswer =: 42\n"
         (c/translate "(def answer 42)"))))

(deftest if-then-else
  (is (= "!ys-0\nif a > 10:\n  say: 'BIG'\n  say: 'small'\n"
         (c/translate "(if (> a 10) (println \"BIG\") (println \"small\"))"))))

(deftest when-no-else
  (is (= "!ys-0\nwhen (empty? a):\n  say: 'empty'\n"
         (c/translate "(when (empty? a) (println \"empty\"))"))))

(deftest cond-pairs
  (is (= "!ys-0\ncond:\n  a < 5: 'S'\n  a < 10: 'M'\n  else: 'L'\n"
         (c/translate "(cond (< a 5) \"S\" (< a 10) \"M\" :else \"L\")"))))

(deftest doseq-becomes-each
  (is (= "!ys-0\neach x (fizzbuzz 1 100):\n  say: x\n"
         (c/translate "(doseq [x (fizzbuzz 1 100)] (println x))"))))

(deftest call-pair-with-args
  (is (= "!ys-0\ngreet: 'Bob'\n"
         (c/translate "(greet \"Bob\")"))))

(deftest zero-arg-call-pair
  (is (= "!ys-0\nhello:\n"
         (c/translate "(hello)"))))

(deftest call-pair-for-unknown-fn
  ;; per the tutorial, any call with a symbolic head becomes a mapping pair
  (is (= "!ys-0\nweird: 1\n" (c/translate "(weird 1)"))))

(deftest non-symbolic-head-falls-back-to-arrow
  ;; parens are safe YAML plain scalars; only true indicators need `+`
  (is (= "!ys-0\n=>: ((f 1) 2)\n" (c/translate "((f 1) 2)"))))

(deftest fizzbuzz-golden
  (let [clj "(defn fizzbuzz [start finish]
  (map (fn [n]
         (cond
           (zero? (mod n 15)) \"FizzBuzz\"
           (zero? (mod n 3)) \"Fizz\"
           (zero? (mod n 5)) \"Buzz\"
           :else n))
       (range start finish)))

(doseq [x (fizzbuzz 1 101)]
  (println x))"
        ys "!ys-0
defn fizzbuzz(start finish):
  map _ (start .. finish):
    fn(n):
      cond:
        (zero? (mod n 15)): 'FizzBuzz'
        (zero? (mod n 3)): 'Fizz'
        (zero? (mod n 5)): 'Buzz'
        else: n
each x (fizzbuzz 1 101):
  say: x
"]
    (is (= ys (c/translate clj)))))
