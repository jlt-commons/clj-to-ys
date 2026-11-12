# clj-to-ys

Translate Clojure source to [YS (YAMLScript)](https://yamlscript.org) — a
Clojure library for [jolt](https://github.com/jolt-lang/jolt) (no JVM),
built on the jolt port of [instaparse](https://github.com/jlt-commons/instaparse).

YS compiles to Clojure, and most Clojure expressions are already valid YS.
The value of this tool is the idiomatic layer: mapping pairs instead of
parenthesized calls, `defn f(args):` definitions, assignments instead of
`let`, `each` instead of `doseq`, and the operator idioms (`..`, `.inc()`,
interpolated strings). The translations follow the official
[From Clojure to YS](https://yamlscript.org/doc/clj-to-ys/) tutorial.

## Usage

CLI:

    jolt -m clj-to-ys.main program.clj        # file
    cat program.clj | jolt -m clj-to-ys.main  # stdin
    jolt -m clj-to-ys.main --string '(println "hi")'

Library:

    (require '[clj-to-ys.core :as c2y])
    (c2y/translate "(doseq [x (range 1 101)] (println x))")
    ;; => "!ys-0\neach x (1 .. 100):\n  say: x\n"

## What it translates

- top-level calls → mapping pairs (`(greet "Bob")` → `greet: 'Bob'`)
- `def` → assignment (`answer =: 42`)
- `defn` → `defn name(args):` bodies; `([] (f "x"))` + `([a] ...)` default
  arity → `defn f(a='x'):`; true multi-arities → the paren-pair form
- `let` → plain assignments, in order
- `if` / `when` / `cond` → block conditionals with `else:` for `:else`
- `doseq` → `each x coll:` (eager, like YS's own `each`)
- `(map (fn [n] ...) coll)` → `map _ coll:` with the fn body indented
- `(inc x)` → `x.inc()`, comparisons `(> a b)` → `a > b`
- `(str "a" x "!")` → `"a$x!"`
- `println`/`print` → `say`/`print`
- anything else renders as plain Clojure-in-a-scalar, with the YS `+`
  escape when the text is not a valid YAML plain scalar

## Known divergences

YS's `..` range is inclusive, Clojure's `range` is exclusive. Numeric
literal ends are decremented to compensate (`(range 1 101)` → `(1 .. 100)`);
symbol ends are left as-is and stay one element longer. `let` becomes flat
assignments, which is fine at top level but does not preserve local scope.
Forms outside the grammar (metadata-heavy code, reader conditionals) will
be rejected with a parse error rather than mistranslated.

## Develop

    jolt -M:test

Dependency: `io.github.jlt-commons/instaparse` git dep (see deps.edn).

## License

EPL 2.0, the same license as Jolt and Clojure. See [LICENSE](LICENSE).
