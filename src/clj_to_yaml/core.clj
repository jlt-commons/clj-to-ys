(ns clj-to-yaml.core
  "Translate Clojure source to YS (YAMLScript).

  Follows the translation patterns documented in the official YS
  'From Clojure to YS' tutorial and cheat sheet: top level forms become
  mapping pairs, defn uses paren args, let becomes assignments, doseq
  becomes each, range becomes the inclusive .. operator.

  statement-lines returns lines whose own head is at relative column 0 and
  whose nested lines carry relative indentation; callers prefix every line
  with their own indent, so nesting composes.

  Known semantic divergences (documented, mechanical translation):
  * (range a b) -> (a .. b); .. is inclusive so a numeric literal end is
    decremented to compensate; symbol ends are left as-is.
  * str with string parts is folded into an interpolated string."
  (:require [clj-to-yaml.grammar :as g]
            [clj-to-yaml.render :as r]
            [clojure.string :as str]))

(def ^:private comparisons #{">" "<" ">=" "<="})

(declare expr* statement-lines)

;;; ---------------------------------------------------------------- helpers

(defn- list? [form] (= :list (first form)))
(defn- sym? [form] (= :sym (first form)))

(defn- args-of
  "Children of a list form after the head: [:list h a1 a2] -> (a1 a2)."
  [form] (drop 2 form))

(defn- head-of
  "Symbol text of a list form's head, else nil."
  [form]
  (when (list? form)
    (let [h (second form)]
      (when (sym? h) (second h)))))

(defn- strip-vec [form]
  (when (= :vec (first form)) (vec (rest form))))

(defn- fn-args-text
  "Render a defn/fn args vector: [a b] -> (a b), [& xs] -> (*xs)."
  [args]
  (let [parts (reduce (fn [acc arg]
                        (let [text (second arg)]
                          (cond
                            (= "&" text)        (conj acc :var)
                            (= :var (peek acc)) (conj (pop acc) (str "*" text))
                            :else               (conj acc text))))
                      [] args)]
    (str "(" (str/join " " (remove #{:var} parts)) ")")))

;;; ------------------------------------------------------------ expressions

(defn- comparison? [form]
  (and (list? form)
       (= 4 (count form))
       (sym? (second form))
       (comparisons (second (second form)))))

(defn- str-parts->interpolation
  "(str \"a\" name \"!\") -> \"a$name!\" when parts are strings and symbols
  and at least one string is present.  Returns nil otherwise."
  [form]
  (let [parts (args-of form)]
    (when (some #(= :str (first %)) parts)
      (when (every? #(#{:str :sym} (first %)) parts)
        (str "\""
             (str/join (map (fn [part]
                              (if (= :sym (first part))
                                (str "$" (second part))
                                (second part)))
                            parts))
             "\"")))))

(defn- num-dec [text]
  (try (str (dec (Long/parseLong text)))
       (catch Exception _ text)))

(defn- expr*-raw
  "Unescaped expression text with YS operator idioms."
  [form]
  (if (not (list? form))
    (r/raw form)
    (let [head (head-of form)]
      (cond
        (comparison? form)
        (str/join " " [(expr*-raw (nth form 2)) (second (second form)) (expr*-raw (nth form 3))])

        (and head (= "inc" head) (= 1 (count (args-of form))))
        (str (expr*-raw (nth form 2)) ".inc()")

        (and head (= "dec" head) (= 1 (count (args-of form))))
        (str (expr*-raw (nth form 2)) ".dec()")

        (and head (= "range" head) (= 2 (count (args-of form))))
        (let [end (nth form 3)
              hi (if (= :num (first end)) (num-dec (second end)) (expr*-raw end))]
          (str "(" (expr*-raw (nth form 2)) " .. " hi ")"))

        (and head (= "str" head) (seq (args-of form)))
        (or (str-parts->interpolation form)
            (str "(" (str/join " " (map expr*-raw (rest form))) ")"))

        :else
        (str "(" (str/join " " (map expr*-raw (rest form))) ")")))))

(defn expr*
  "Extended expression rendering: a YAML-safe scalar with YS operator
  idioms (infix comparisons, .inc()/.dec(), inclusive .. range, str
  interpolation)."
  [form]
  (r/escape-plain (expr*-raw form)))

;;; ------------------------------------------------------------- statements

(defn- prefix2 [lines] (map #(str "  " %) lines))

(defn- pair-line
  "f args... -> \"f: arg1 arg2\" (bare key for zero args)."
  [head args]
  (if (seq args)
    (str head ": " (str/join " " (map expr* args)))
    (str head ":")))

(defn- parse-arity
  "arity form [:list [:vec ...] body...] -> [args-seq body-seq]."
  [form]
  [(strip-vec (second form)) (drop 2 form)])

(defn- default-arity?
  "True for an arity like ([] (f \"default\")) that calls its own fn with
  a single constant argument -- YS default-arg sugar source."
  [name-text form]
  (let [[args body] (parse-arity form)
        call (first body)]
    (and (vector? args)
         (empty? args)
         (= 1 (count body))
         (= name-text (head-of call))
         (= 1 (count (args-of call))))))

(defn- defn-lines [form]
  (let [name-text (second (nth form 2))
        tail (drop 3 form)   ; single arity: args vec + body; multi: arity lists
        first-args (strip-vec (first tail))]
    (cond
      ;; single arity: args vector follows the name directly
      first-args
      (into [(str "defn " name-text (fn-args-text first-args) ":")]
            (mapcat #(prefix2 (statement-lines %)) (rest tail)))

      ;; zero-arg arity calling self with one constant + normal arity
      ;; -> default argument sugar (single-arg main arity)
      (and (= 2 (count tail))
           (default-arity? name-text (first tail))
           (= 1 (count (first (parse-arity (second tail))))))
      (let [[args body] (parse-arity (second tail))
            zero-call (first (second (parse-arity (first tail))))
            default-text (expr*-raw (first (args-of zero-call)))]
        (into [(str "defn " name-text "(" (second (first args)) "=" default-text "):")]
              (mapcat #(prefix2 (statement-lines %)) body)))

      ;; multi arity: one (args): body pair per arity
      :else
      (into [(str "defn " name-text ":")]
            (map (fn [arity]
                   (let [[args body] (parse-arity arity)]
                     (str "  " (fn-args-text args) ": "
                          (str/join " " (map expr* body)))))
                 tail)))))

(defn- let-lines [form]
  (let [bindings (partition 2 (strip-vec (nth form 2)))
        body (drop 3 form)]
    (into (mapv (fn [binding]
                  (str (second (first binding)) " =: " (expr* (second binding))))
                bindings)
          (mapcat statement-lines body))))

(defn- if-lines [form]
  (into [(str "if " (expr* (nth form 2)) ":")]
        (concat (prefix2 (statement-lines (nth form 3)))
                (prefix2 (statement-lines (nth form 4))))))

(defn- when-lines [form]
  (into [(str "when " (expr* (nth form 2)) ":")]
        (mapcat #(prefix2 (statement-lines %)) (drop 3 form))))

(defn- cond-lines [form]
  (into ["cond:"]
        (map (fn [clause]
               (let [test (first clause)
                     val (second clause)
                     test-text (if (and (= :kw (first test)) (= ":else" (second test)))
                                 "else"
                                 (expr* test))]
                 (str "  " test-text ": " (expr* val))))
             (partition 2 (drop 2 form)))))

(defn- doseq-lines [form]
  (let [binding-children (strip-vec (nth form 2))
        bound (first binding-children)
        coll (second binding-children)
        body (drop 3 form)]
    (into [(str "each " (second bound) " " (expr* coll) ":")]
          (mapcat #(prefix2 (statement-lines %)) body))))

(defn- fn-lines [form]
  (into [(str "fn" (fn-args-text (strip-vec (nth form 2))) ":")]
        (mapcat #(prefix2 (statement-lines %)) (drop 3 form))))

(defn- map-lines [form]
  (into [(str "map _ " (expr* (nth form 3)) ":")]
        (prefix2 (statement-lines (nth form 2)))))

(defn statement-lines
  "Translate one top level (or body) form into YS statement lines."
  [form]
  (if (not (list? form))
    [(str "=>: " (expr* form))]
    (let [head (head-of form)]
      (cond
        (= "def" head)     [(str (second (nth form 2)) " =: " (expr* (nth form 3)))]
        (= "defn" head)    (defn-lines form)
        (= "let" head)     (let-lines form)
        (= "if" head)      (if-lines form)
        (= "when" head)    (when-lines form)
        (= "cond" head)    (cond-lines form)
        (= "doseq" head)   (doseq-lines form)
        (= "fn" head)      (fn-lines form)
        (and (= "map" head) (= "fn" (head-of (nth form 2))))
        (map-lines form)
        (= "println" head) (if (seq (args-of form))
                             [(str "say: " (expr* (nth form 2)))]
                             ["say:"])
        (= "print" head)   [(str "print: " (expr* (nth form 2)))]
        head               [(pair-line head (args-of form))]
        :else              [(str "=>: " (expr* form))]))))

(defn translate
  "Translate Clojure source text to a YS program string."
  [source]
  (let [forms (g/parse-forms source)
        lines (mapcat statement-lines forms)]
    (str (str/join "\n" (into ["!ys-0"] lines)) "\n")))
