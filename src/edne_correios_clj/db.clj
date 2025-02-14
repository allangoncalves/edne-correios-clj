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

(defn bulk-insert
  [table-name values]
  (jdbc/execute! ds (sql/format {:replace-into table-name
                                 :values values})))

(defn execute! [q]
  (jdbc/execute! ds (sql/format (debug q))))

(defn create-table
  [table-name columns]
  (execute! {:create-table table-name
             :with-columns columns}))
