(ns edne-correios-clj.db
  (:require [edne-correios-clj.db-schemas :as db-schemas]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.plan :as plan]))

(defn debug [el]
  (def el-debug el)
  (do (clojure.pprint/pprint el)
      el))

(defn execute! [conn q]
  (jdbc/execute! conn (sql/format q)))

(defn bulk-insert!
  [conn table-name values]
  (execute! conn {:replace-into table-name
             :values values}))

(defn insert!
  [conn table-name value]
  (bulk-insert! conn table-name [value]))

(defn delete-from
  [conn table-name where-filters]
  (execute! conn {:delete-from table-name :where where-filters}))

(defn create-table
  [conn table-name columns]
  (execute! conn {:create-table [table-name :if-not-exists]
                  :with-columns columns}))

(defn create-tables
  [conn]
  (doseq [[table-name {:keys [columns]}] db-schemas/tables]
    (create-table conn table-name columns)))

(def cep-view
  "CREATE VIEW ceps AS
SELECT
    log_logradouro.cep AS cep,
    CONCAT(log_logradouro.tlo_tx, ' ', log_logradouro.log_no) AS endereco,
    log_bairro.bai_no AS bairro,
    log_localidade.loc_no AS cidade,
    log_logradouro.ufe_sg AS uf,
    log_logradouro.log_complemento AS complemento,
    '' AS nome
FROM
    log_logradouro,
    log_localidade,
    log_bairro
WHERE
    log_logradouro.loc_nu = log_localidade.loc_nu
    AND log_logradouro.bai_nu_ini = log_bairro.bai_nu
    AND log_logradouro.log_sta_tlo = 'S'

UNION

SELECT
    log_logradouro.cep AS cep,
    log_logradouro.log_no AS endereco,
    log_bairro.bai_no AS bairro,
    log_localidade.loc_no AS cidade,
    log_logradouro.ufe_sg AS uf,
    log_logradouro.log_complemento AS complemento,
    '' AS nome
FROM
    log_logradouro,
    log_localidade,
    log_bairro
WHERE
    log_logradouro.loc_nu = log_localidade.loc_nu
    AND log_logradouro.bai_nu_ini = log_bairro.bai_nu
    AND log_logradouro.log_sta_tlo = 'N'

UNION

SELECT
    loc.cep AS cep,
    '' AS endereco,
    '' AS bairro,
    loc.loc_no AS cidade,
    loc.ufe_sg AS uf,
    '' AS complemento,
    '' AS nome
FROM
    log_localidade AS loc
WHERE
    loc.cep IS NOT NULL
    AND loc.loc_nu_sub IS NULL

UNION

SELECT
    loc.cep AS cep,
    '' AS endereco,
    loc.loc_no AS bairro,
    locsub.loc_no AS cidade,
    loc.ufe_sg AS uf,
    '' AS complemento,
    '' AS nome
FROM
    log_localidade AS loc,
    log_localidade AS locsub
WHERE
    loc.cep IS NOT NULL
    AND loc.loc_nu_sub IS NOT NULL
    AND loc.loc_nu_sub = locsub.loc_nu

UNION

SELECT
    log_cpc.cep AS cep,
    log_cpc.cpc_endereco AS endereco,
    '' AS bairro,
    log_localidade.loc_no AS cidade,
    log_cpc.ufe_sg AS uf,
    '' AS complemento,
    cpc_no AS nome
FROM
    log_cpc,
    log_localidade
WHERE
    log_cpc.loc_nu = log_localidade.loc_nu

UNION

SELECT
    log_grande_usuario.cep AS cep,
    log_grande_usuario.gru_endereco AS endereco,
    log_bairro.bai_no AS bairro,
    log_localidade.loc_no AS cidade,
    log_grande_usuario.ufe_sg AS uf,
    '' AS complemento,
    gru_no AS nome
FROM
    log_grande_usuario,
    log_localidade,
    log_bairro
WHERE
    log_grande_usuario.loc_nu = log_localidade.loc_nu
    AND log_grande_usuario.bai_nu = log_bairro.bai_nu

UNION

SELECT
    log_unid_oper.cep AS cep,
    log_unid_oper.uop_endereco AS endereco,
    log_bairro.bai_no AS bairro,
    log_localidade.loc_no AS cidade,
    log_unid_oper.ufe_sg AS uf,
    '' AS complemento,
    uop_no AS nome
FROM
    log_unid_oper,
    log_localidade,
    log_bairro
WHERE
    log_unid_oper.loc_nu = log_localidade.loc_nu
    AND log_unid_oper.bai_nu = log_bairro.bai_nu")

(defn create-cep-view
  [conn]
  (jdbc/execute! conn [cep-view]))

(defn fetch-ceps
  [conn]
  #_(sql/find-by-keys ds :ceps :all {:order-by [:cep] :offset 0 :limit 10})
  (plan/select! conn
                (juxt :cep :endereco :bairro :cidade :uf :uf_nome)
                ["SELECT
                    *,
                    CASE ceps.uf
                      WHEN 'AC' THEN 'Acre'
                      WHEN 'AL' THEN 'Alagoas'
                      WHEN 'AP' THEN 'Amapá'
                      WHEN 'AM' THEN 'Amazonas'
                      WHEN 'BA' THEN 'Bahia'
                      WHEN 'CE' THEN 'Ceará'
                      WHEN 'DF' THEN 'Distrito Federal'
                      WHEN 'ES' THEN 'Espírito Santo'
                      WHEN 'GO' THEN 'Goiás'
                      WHEN 'MA' THEN 'Maranhão'
                      WHEN 'MT' THEN 'Mato Grosso'
                      WHEN 'MS' THEN 'Mato Grosso do Sul'
                      WHEN 'MG' THEN 'Minas Gerais'
                      WHEN 'PA' THEN 'Pará'
                      WHEN 'PB' THEN 'Paraíba'
                      WHEN 'PR' THEN 'Paraná'
                      WHEN 'PE' THEN 'Pernambuco'
                      WHEN 'PI' THEN 'Piauí'
                      WHEN 'RJ' THEN 'Rio de Janeiro'
                      WHEN 'RN' THEN 'Rio Grande do Norte'
                      WHEN 'RS' THEN 'Rio Grande do Sul'
                      WHEN 'RO' THEN 'Rondônia'
                      WHEN 'RR' THEN 'Rorâima'
                      WHEN 'SC' THEN 'Santa Catarina'
                      WHEN 'SP' THEN 'São Paulo'
                      WHEN 'SE' THEN 'Sergipe'
                      WHEN 'TO' THEN 'Tocantins'
                    END AS uf_nome
                 FROM ceps"]))
