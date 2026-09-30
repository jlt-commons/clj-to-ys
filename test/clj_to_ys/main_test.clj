(ns clj-to-ys.main-test
  (:require [clojure.test :refer [deftest is]]
            [clj-to-ys.main :as m]))

(deftest translate-string-entry
  (is (= "!ys-0\nsay: 'hi'\n" (m/-main "--string" "(println \"hi\")"))))

(deftest translate-file-entry
  (let [tmp (java.io.File/createTempFile "clj2ys" ".clj")]
    (try
      (spit tmp "(println \"from file\")")
      (is (= "!ys-0\nsay: 'from file'\n" (m/-main (str tmp))))
      (finally (.delete tmp)))))

(deftest reads-stdin-when-no-args
  (is (= "!ys-0\nsay: 'stdin'\n" (with-in-str "(println \"stdin\")" (m/-main)))))
