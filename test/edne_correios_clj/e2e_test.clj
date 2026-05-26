(ns edne-correios-clj.e2e-test
  (:require [clojure.test :refer [deftest is]]
            [edne-correios-clj.core :as core]
            [next.jdbc :as jdbc])
  (:import [java.io StringWriter]))

(def log-dir   "test/fixtures/edne-mini/log")
(def delta-dir "test/fixtures/edne-mini/delta")
(def golden    "test/fixtures/edne-mini/expected.csv")

(deftest fixture-pipeline-matches-golden
  ;; Carrega DB em memória + escreve o CSV no StringWriter — sem temp files,
  ;; sem try/finally. A connection é dona daqui, e core/execute + core/write-ceps
  ;; são compostos diretamente.
  (let [out (StringWriter.)]
    (with-open [conn (jdbc/get-connection (jdbc/get-datasource
                                            {:dbtype "sqlite" :dbname ":memory:"}))]
      (core/execute   conn log-dir delta-dir)
      (core/write-ceps conn out))
    (is (= (slurp golden) (str out)))))
