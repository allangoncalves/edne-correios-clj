(ns edne-correios-clj.db
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]))

(defn debug [el]
  (def el-debug el)
  (do (clojure.pprint/pprint el)
      el))

(def db-spec
  {:dbtype "sqlite"
   :dbname "example.db"})

(def ds (jdbc/get-datasource db-spec))

(defn execute! [q]
  (jdbc/execute! ds (sql/format q)))

(defn bulk-insert!
  [table-name values]
  (execute! {:replace-into table-name
             :values values}))

(defn insert!
  [table-name value]
  (bulk-insert! table-name [value]))

(defn delete-from
  [table-name where-filters]
  (execute! {:delete-from table-name :where where-filters}))

(defn create-table
  [table-name columns]
  (execute! {:create-table [table-name :if-not-exists]
             :with-columns columns}))
