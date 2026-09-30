(ns clj-to-yaml.render
  "Render grammar HIR to YS expression text.

  Forms are flat vectors: [:list c1 c2 ...].  `raw` builds expression text
  without YAML escaping (children stay raw so composites escape once);
  `expr` is the escaped scalar form.  YS code mode allows only YAML plain,
  quoted, and literal scalars, so text that would not re-read as the same
  plain scalar gets a leading `+`, which YS strips on read.")

(declare raw)

(defn- escape-string
  "YS prefers single quotes; fall back to double when the text contains one."
  [text]
  (if (.contains text "'")
    (str "\"" text "\"")
    (str "'" text "'")))

(defn- needs-escape?
  "True when text is not safe as a YAML plain scalar.  YAML allows plain
  scalars to start with - ? : when not followed by whitespace, but a
  leading + must be escaped so YS does not strip it."
  [text]
  (boolean
   (or (re-matches #"[!&*#%@`\|>,\[\]{}].*|\+.*" text)
       (re-matches #"[-?:](\s.*|)" text)
       (re-matches #"'[^']*'.+|\"[^\"]*\".+" text)
       (.contains text ": ")
       (.contains text " #"))))

(defn escape-plain
  "Prefix `+` when the text cannot stand as a YAML plain scalar."
  [text]
  (if (needs-escape? text) (str "+" text) text))

(defn- join-args
  "Render a form's children (after the tag) space-separated, raw."
  [form]
  (clojure.string/join " " (map raw (rest form))))

(defn raw
  "Render a HIR form to unescaped YS expression text."
  [form]
  (let [tag (first form)]
    (case tag
      :sym (second form)
      :num (second form)
      :bool (second form)
      :nil (second form)
      :kw (second form)
      :str (escape-string (second form))
      :regex (str "#\"" (second form) "\"")
      :list (str "(" (join-args form) ")")
      :vec (str "[" (join-args form) "]")
      :map (str "{" (join-args form) "}")
      :set (str "#{" (join-args form) "}")
      :deref (str "(deref " (raw (second form)) ")")
      :syntax-quote (str "`" (raw (second form)))
      :unquote (str "~" (raw (second form)))
      :unquote-splicing (str "~@" (raw (second form)))
      (throw (ex-info (str "Unknown HIR tag: " tag) {:form form})))))

(defn expr
  "Render a HIR form to YS expression text (a YAML-safe scalar)."
  [form]
  (escape-plain (raw form)))
