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

(def cep-view
  "CREATE VIEW ceps AS
   SELECT COALESCE(log.cep, loc.cep) AS CEP,
     CASE log.log_sta_tlo
      WHEN 'S' THEN
      COALESCE(log.tlo_tx, '') || ' ' || COALESCE(log.log_no, '')
       ELSE
       COALESCE(log.log_no, '')
     END AS ENDERECO,
      COALESCE(bai.bai_no, '') AS BAIRRO,
   loc.loc_no AS CIDADE,
     loc.ufe_sg AS UF
     FROM log_localidade loc
     LEFT JOIN log_logradouro log ON log.loc_nu = loc.loc_nu
     LEFT JOIN log_bairro bai ON log.bai_nu_ini = bai.bai_nu

     UNION ALL

     SELECT uni.cep AS CEP,
       uni.uop_endereco AS ENDERECO,
    COALESCE(bai.bai_no, '') AS BAIRRO,
      loc.loc_no AS CIDADE,
   uni.ufe_sg AS UF
   FROM log_unid_oper uni
   LEFT JOIN log_bairro bai ON uni.bai_nu = bai.bai_nu
   LEFT JOIN log_localidade loc ON uni.loc_nu = loc.loc_nu

   UNION ALL

   SELECT gra.cep AS CEP,
     gra.gru_endereco AS ENDERECO,
       COALESCE(bai.bai_no, '') AS BAIRRO,
    loc.loc_no AS CIDADE,
      gra.ufe_sg AS UF
      FROM log_grande_usuario gra
      LEFT JOIN log_bairro bai ON gra.bai_nu = bai.bai_nu
      LEFT JOIN log_localidade loc ON gra.loc_nu = loc.loc_nu")

(defn create-cep-view
  []
  (jdbc/execute! ds [cep-view]))
