(ns edne-correios-clj.db
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;;; ============================================================
;;; Schemas
;;; ============================================================

(def ^:private ufs
  ["AC" "AL" "AM" "AP" "BA" "CE" "DF" "ES" "GO" "MA" "MG" "MS" "MT"
   "PA" "PB" "PE" "PI" "PR" "RJ" "RN" "RO" "RR" "RS" "SC" "SE" "SP" "TO"])

(def tables
  {:log_localidade
   {:files   ["LOG_LOCALIDADE.TXT"
              "DELTA_LOG_LOCALIDADE.TXT"]
    :columns [[:loc_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_no :text]
              [:cep :text]
              [:loc_in_sit :text]
              [:loc_in_tipo_loc :text]
              [:loc_nu_sub :integer]
              [:loc_no_abrev :text]
              [:mun_nu :text]]}

   :log_bairro
   {:files   ["LOG_BAIRRO.TXT"
              "DELTA_LOG_BAIRRO.TXT"]
    :columns [[:bai_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_no :text]
              [:bai_no_abrev :text]]}

   :log_cpc
   {:files   ["LOG_CPC.TXT"
              "DELTA_LOG_CPC.TXT"]
    :columns [[:cpc_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:cpc_no :text]
              [:cpc_endereco :text]
              [:cep :text]]}

   :log_logradouro
   {:files   (-> (mapv #(str "LOG_LOGRADOURO_" % ".TXT") ufs)
                 (conj "DELTA_LOG_LOGRADOURO.TXT"))
    :columns [[:log_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu_ini :integer]
              [:bai_nu_fim :integer]
              [:log_no :text]
              [:log_complemento :text]
              [:cep :text]
              [:tlo_tx :text]
              [:log_sta_tlo :text]
              [:log_no_abrev :text]]}

   :log_grande_usuario
   {:files   ["LOG_GRANDE_USUARIO.TXT"
              "DELTA_LOG_GRANDE_USUARIO.TXT"]
    :columns [[:gru_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu :integer]
              [:log_nu :integer]
              [:gru_no :text]
              [:gru_endereco :text]
              [:cep :text]
              [:gru_no_abrev :text]]}

   :log_unid_oper
   {:files   ["LOG_UNID_OPER.TXT"
              "DELTA_LOG_UNID_OPER.TXT"]
    :columns [[:uop_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu :integer]
              [:log_nu :integer]
              [:uop_no :text]
              [:uop_endereco :text]
              [:cep :text]
              [:uop_in_cp :text]
              [:uop_no_abrev :text]]}})

(def file->table
  (into {}
        (for [[table {:keys [files]}] tables
              fname files]
          [fname table])))

;;; ============================================================
;;; SQL
;;; ============================================================

(defn bulk-insert!
  [conn table-name values]
  (let [row-ph (->> (repeat (count (first values)) "?")
                    (str/join ", ")
                    (format "(%s)"))
        sql    (->> (repeat (count values) row-ph)
                    (str/join ", ")
                    (str "INSERT OR REPLACE INTO " (name table-name) " VALUES "))]
    (jdbc/execute! conn (into [sql] cat values))))

(defn insert!
  [conn table-name value]
  (bulk-insert! conn table-name [value]))

(defn delete-from
  [conn table-name [_op col value]]
  (jdbc/execute! conn
                 [(str "DELETE FROM " (name table-name)
                       " WHERE " (name col) " = ?")
                  value]))

(defn create-table
  [conn table-name columns]
  (let [col-defs (->> columns
                      (map (fn [[col-name col-type & modifiers]]
                             (->> modifiers
                                  (map #(-> % name (str/replace "-" " ")))
                                  (concat [(name col-name) (name col-type)])
                                  (str/join " "))))
                      (str/join ", "))
        sql (str "CREATE TABLE IF NOT EXISTS " (name table-name) " (" col-defs ")")]
    (jdbc/execute! conn [sql])))

(defn create-tables
  [conn]
  (doseq [[table-name {:keys [columns]}] tables]
    (create-table conn table-name columns)))

;;; ============================================================
;;; CEP
;;; ============================================================

(def ceps-sql
  "WITH ceps AS (
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
    AND log_unid_oper.bai_nu = log_bairro.bai_nu
)
SELECT
    cep,
    endereco,
    bairro,
    cidade,
    uf,
    complemento,
    nome,
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
   FROM ceps")

(defn fetch-ceps
  [conn]
  (jdbc/plan conn
             [ceps-sql]
             {:fetch-size 10000
              :builder-fn rs/as-unqualified-maps}))
