(ns edne-correios-clj.e2e-test
  (:require [clojure.test :refer [deftest is]]
            [edne-correios-clj.core :as core])
  (:import [java.io StringWriter]))

(def log-dir   "test/fixtures/edne-mini/log")
(def delta-dir "test/fixtures/edne-mini/delta")
(def golden    "test/fixtures/edne-mini/expected.csv")

(deftest fixture-pipeline-matches-golden
  ;; A StringWriter accumulates the CSV in memory — no temp file to delete,
  ;; no try/finally. core/execute accepts anything io/writer can wrap.
  (let [out (StringWriter.)]
    (core/execute ":memory:" log-dir delta-dir out)
    (is (= (slurp golden) (str out)))))
