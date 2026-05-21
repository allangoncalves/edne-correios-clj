(ns edne-correios-clj.db-schemas)

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
