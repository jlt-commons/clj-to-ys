(ns clj-to-ys.grammar
  "Clojure reader grammar (the portable scripting subset) via instaparse.
  Produces a token-text HIR: scalars keep their source text verbatim so
  rendering is lossless.

  Scalar words (numbers, booleans, nil, symbols) share one `word` rule and
  are disambiguated after parsing: instaparse is GLL, so alternation order
  does not reliably prefer number/bool/nil over symbol."
  (:require [instaparse.core :as insta]
            [clojure.string :as str]))

(def ^:private grammar-text
  "forms = <spacing?> form* <spacing?>
   <form> = <spacing?> item
   spacing = (ws | comment)*
   ws = <#\"[ ,\\r\\n\\t]+\">
   comment = <#\";[^\\r\\n]*\">

   <item> = list | vector | map | set | discard | var-ref | regex-literal
          | syntax-quote | unquote-splicing | unquote | quote | deref
          | meta-scalar | meta-map
          | string | keyword | word

   list = <'('> <spacing?> form* <spacing?> <')'>
   vector = <'['> <spacing?> form* <spacing?> <']'>
   set = <'#{'> <spacing?> form* <spacing?> <'}'>
   map = <'{'> <spacing?> pair* <spacing?> <'}'>
   pair = form form

   discard = <'#_'> form
   var-ref = <\"#'\"> form
   regex-literal = <'#'> string-literal
   quote = <\"'\"> form
   syntax-quote = <'`'> form
   unquote-splicing = <'~@'> form
   unquote = <#'~(?!@)'> form
   deref = <'@'> form
   meta-scalar = <#\"\\^[^\\s()\\[\\]{}]+\"> <spacing?> form
   meta-map = <'^'> <spacing?> map <spacing?> form

   string = string-literal
   string-literal = <'\"'> #'([^\"\\\\]|\\\\.)*' <'\"'>
   keyword = #':{1,2}[^ ,\\r\\n\\t()\\[\\]{}@~]+'
   word = #'[^ ,\\r\\n\\t()\\[\\]{}@~\\\"\\'`^~\\\\;:#]+'")

(defn- clean-children
  "Drop the nil placeholders left by discarded forms."
  [children]
  (vec (remove nil? children)))

(defn- re-tag-word
  "Classify a word token by full-match: number, boolean, nil, or symbol."
  [text]
  (cond
    (re-matches #"0[xX][0-9a-fA-F]+" text)            [:num text]
    (re-matches #"[+-]?[0-9]+/[0-9]+" text)           [:num text]
    (re-matches #"[+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?" text) [:num text]
    (= "true" text)                                   [:bool text]
    (= "false" text)                                  [:bool text]
    (= "nil" text)                                    [:nil text]
    :else                                             [:sym text]))

(def ^:private transform-map
  {:forms       (fn [& forms] (clean-children forms))
   :list        (fn [& children] (into [:list] (clean-children children)))
   :vector      (fn [& children] (into [:vec] (clean-children children)))
   :set         (fn [& children] (into [:set] (clean-children children)))
   :pair        (fn [& children] children)
   :map         (fn [& pairs] (into [:map] (clean-children (apply concat pairs))))
   :quote       (fn [form] [:list [:sym "quote"] form])
   :var-ref     (fn [form] [:list [:sym "var"] form])
   :string-literal (fn [raw] [:str raw])
   :string      (fn [form] form)
   :regex-literal (fn [form] [:regex (second form)])
   :keyword     (fn [text] [:kw text])
   :word        re-tag-word
   :syntax-quote (fn [form] [:syntax-quote form])
   :unquote     (fn [form] [:unquote form])
   :unquote-splicing (fn [form] [:unquote-splicing form])
   :deref       (fn [form] [:deref form])
   :meta-scalar (fn [form] form)
   :meta-map    (fn [_ form] form)
   :discard     (fn [_] nil)})

(defn parse-forms
  "Parse Clojure source into a vector of HIR forms. Throws on invalid input."
  [source]
  (let [parse-fn (insta/parser grammar-text)
        tree     (insta/parse parse-fn source)]
    (if (insta/failure? tree)
      (throw (ex-info (str "Parse error: " (pr-str (insta/get-failure tree)))
                      {:source source}))
      (insta/transform transform-map tree))))
