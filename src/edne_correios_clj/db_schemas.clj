(ns edne-correios-clj.db-schemas)

(def tables
  {:log_faixa_uf
   {:columns [[:ufe_sg :text]
              [:ufe_cep_ini :text]
              [:ufe_cep_fim :text]]
    :file-name-regex #".*LOG_FAIXA_UF.*"}

   :log_localidade
   {:columns [[:loc_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_no :text]
              [:cep :text]
              [:loc_in_sit :text]
              [:loc_in_tipo_loc :text]
              [:loc_nu_sub :integer]
              [:loc_no_abrev :text]
              [:mun_nu :text]]
    :file-name-regex #".*LOG_LOCALIDADE.*"}

   :log_var_loc
   {:columns [[:loc_nu :integer :primary-key]
              [:val_nu :integer]
              [:val_tx :text]]
    :file-name-regex #".*LOG_VAR_LOC.*"}

   :log_faixa_localidade
   {:columns [[:loc_nu :integer :primary-key]
              [:loc_cep_ini :text]
              [:loc_cep_fim :text]
              [:loc_tipo_faixa :text]]
    :file-name-regex #".*LOG_FAIXA_LOC.*"}

   :bairros
   {:columns [[:bai_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_no :text]
              [:bai_no_abrev :text]]
    :file-name-regex #".*LOG_BAIRRO.*"}

   :log_var_bai
   {:columns [[:bai_nu :integer :primary-key]
              [:vdb_nu :integer]
              [:vdb_tx :text]]
    :file-name-regex #".*LOG_VAR_BAI.*"}

   :log_faixa_bairro
   {:columns [[:bai_nu :integer :primary-key]
              [:fcb_cep_ini :text]
              [:fcb_cep_fim :text]]
    :file-name-regex #".*LOG_FAIXA_BAI.*"}

   :log_cpc
   {:columns [[:cpc_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:cpc_no :text]
              [:cpc_endereco :text]
              [:cep :text]]
    :file-name-regex #".*LOG_CPC.*"}

   :log_faixa_cpc
   {:columns [[:cpc_nu :integer :primary-key]
              [:cpc_inicial :text]
              [:cpc_final :text]]
    :file-name-regex #".*LOG_FAIXA_CPC.*"}

   :logradouros
   {:columns [[:log_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu_ini :integer]
              [:bai_nu_fim :integer]
              [:log_no :text]
              [:log_complemento :text]
              [:cep :text]
              [:tlo_tx :text]
              [:log_sta_tlo :text]
              [:log_no_abrev :text]]
    :file-name-regex #".*LOG_LOGRADOURO.*"}

   :log_var_log
   {:columns [[:log_nu :integer :primary-key]
              [:vlo_nu :integer]
              [:tlo_tx :text]
              [:vlo_tx :text]]
    :file-name-regex #".*LOG_VAR_LOG.*"}

   :log_num_sec
   {:columns [[:log_nu :integer :primary-key]
              [:sec_nu_ini :text]
              [:sec_nu_fim :text]
              [:sec_in_lado :text]]
    :file-name-regex #".*LOG_NUM_SEC.*"}

   :log_grande_usuario
   {:columns [[:gru_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu :integer]
              [:log_nu :integer]
              [:gru_no :text]
              [:gru_endereco :text]
              [:cep :text]
              [:gru_no_abrev :text]]
    :file-name-regex #".*LOG_GRANDE_USUARIO.*"}

   :log_unid_oper
   {:columns [[:uop_nu :integer :primary-key]
              [:ufe_sg :text]
              [:loc_nu :integer]
              [:bai_nu :integer]
              [:log_nu :integer]
              [:uop_no :text]
              [:uop_endereco :text]
              [:cep :text]
              [:uop_in_cp :text]
              [:uop_no_abrev :text]]
    :file-name-regex #".*LOG_UNID_OPER.*"}

   :log_faixa_uop
   {:columns [[:uop_nu :integer :primary-key]
              [:fnc_inicial :integer]
              [:fnc_final :integer]]
    :file-name-regex #".*LOG_FAIXA_UOP.*"}

   :ect_pais
   {:columns [[:pai_sg :text]
              [:pai_sg_alternativa :text]
              [:pai_no_portugues :text]
              [:pai_no_ingles :text]
              [:pai_no_frances :text]
              [:pai_abreviatura :text]]
    :file-name-regex #".*ECT_PAIS.*"}})
