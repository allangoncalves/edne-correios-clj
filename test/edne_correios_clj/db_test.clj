(ns edne-correios-clj.db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [edne-correios-clj.db :as db]
            [matcher-combinators.matchers :refer [embeds nested-equals]]
            [matcher-combinators.test     :refer [match?]]
            [next.jdbc :as jdbc]))

(def ^:dynamic *conn* nil)

(defn with-fresh-db [t]
  (with-open [conn (jdbc/get-connection (jdbc/get-datasource
                                         {:dbtype "sqlite" :dbname ":memory:"}))]
    (db/create-tables conn)
    (binding [*conn* conn] (t))))

(use-fixtures :each with-fresh-db)

(defn rows [conn table-name]
  (jdbc/execute! conn [(str "SELECT * FROM " (name table-name) " ORDER BY rowid")]))

(deftest create-tables-produces-valid-ddl-for-every-table
  (testing "every table key in db/tables exists in sqlite_master after create-tables"
    (let [created (->> (jdbc/execute! *conn* ["SELECT name FROM sqlite_master WHERE type='table'"])
                       (map :sqlite_master/name)
                       set)]
      (is (match? (embeds (set (map name (keys db/tables))))
                  created)))))

(deftest bulk-insert-preserves-every-column
  (testing "bulk-insert! of 3 rows round-trips with EXACTLY the columns and values supplied"
    (db/bulk-insert! *conn* :log_bairro [["100" "SP" "1" "Bela Vista" "Bela Vista"]
                                         ["101" "SP" "1" "Centro"     "Centro"]
                                         ["102" "RJ" "2" "Copacabana" "Copa"]])
    (is (match? (nested-equals
                  [{:log_bairro/bai_nu 100 :log_bairro/ufe_sg "SP" :log_bairro/loc_nu 1
                    :log_bairro/bai_no "Bela Vista" :log_bairro/bai_no_abrev "Bela Vista"}
                   {:log_bairro/bai_nu 101 :log_bairro/ufe_sg "SP" :log_bairro/loc_nu 1
                    :log_bairro/bai_no "Centro"     :log_bairro/bai_no_abrev "Centro"}
                   {:log_bairro/bai_nu 102 :log_bairro/ufe_sg "RJ" :log_bairro/loc_nu 2
                    :log_bairro/bai_no "Copacabana" :log_bairro/bai_no_abrev "Copa"}])
                (rows *conn* :log_bairro)))))

(deftest insert-or-replace-on-duplicate-pk
  (testing "second insert with same PK replaces; result vector has length 1"
    (db/bulk-insert! *conn* :log_bairro [["50" "SP" "1" "First name" "First"]])
    (db/bulk-insert! *conn* :log_bairro [["50" "RJ" "9" "Second name" "Second"]])
    (is (match? [{:log_bairro/ufe_sg "RJ"
                  :log_bairro/bai_no "Second name"}]
                (rows *conn* :log_bairro)))))

(deftest delete-from-only-touches-matched-row
  (db/bulk-insert! *conn* :log_bairro [["1" "SP" "1" "A" "A"]
                                       ["2" "SP" "1" "B" "B"]
                                       ["3" "SP" "1" "C" "C"]])
  (db/delete-from *conn* :log_bairro [:= :bai_nu "2"])
  (testing "row 2 is gone, rows 1 and 3 remain in order"
    (is (match? [{:log_bairro/bai_nu 1 :log_bairro/bai_no "A"}
                 {:log_bairro/bai_nu 3 :log_bairro/bai_no "C"}]
                (rows *conn* :log_bairro)))))

(deftest single-row-insert-wrapper
  (testing "insert! is just bulk-insert! of a 1-element vector"
    (db/insert! *conn* :log_bairro ["7" "SP" "1" "X" "X"])
    (is (match? [{:log_bairro/bai_nu 7 :log_bairro/bai_no "X"}]
                (rows *conn* :log_bairro)))))

(deftest nil-parameters-bind-as-sql-null
  (testing "nil in the row vector becomes SQL NULL, not the string 'nil'"
    (db/bulk-insert! *conn* :log_bairro [["8" "SP" "1" "Has-name" nil]])
    (is (match? [{:log_bairro/bai_no       "Has-name"
                  :log_bairro/bai_no_abrev nil}]
                (rows *conn* :log_bairro)))))
