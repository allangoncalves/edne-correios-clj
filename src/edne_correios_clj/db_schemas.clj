(ns edne-correios-clj.db-schemas)

(def log-faixa-uf-table
  {:table-name :log_faixa_uf
   :columns [[:ufe_sg :text]
             [:ufe_cep_ini :text]
             [:ufe_cep_fim :text]]
   :file-name-regex #".*LOG_FAIXA_UF\.TXT"})

(def log-localidade-table
  {:table-name :log_localidade
   :columns [[:loc_nu :integer :primary-key]
             [:ufe_sg :text]
             [:loc_no :text]
             [:cep :text]
             [:loc_in_sit :text]
             [:loc_in_tipo_loc :text]
             [:loc_nu_sub :integer]
             [:loc_no_abrev :text]
             [:mun_nu :text]]
   :file-name-regex #".*LOG_LOCALIDADE\.TXT"})


(def log-var-loc-table
  {:table-name :log_var_loc
   :columns [[:loc_nu :integer :primary-key]
             [:val_nu :integer]
             [:val_tx :text]]
   :file-name-regex #".*LOG_VAR_LOC\.TXT"})



(def log-faixa-localidade-table
  {:table-name :log_faixa_localidade
   :columns [[:loc_nu :integer :primary-key]
             [:loc_cep_ini :text]
             [:loc_cep_fim :text]
             [:loc_tipo_faixa :text]]
   :file-name-regex #".*LOG_FAIXA_LOCALIDADE\.TXT"})

(def bairros-table
  {:table-name :bairros
   :columns [[:bai_nu :integer :primary-key]
             [:ufe_sg :text]
             [:loc_nu :integer]
             [:bai_no :text]
             [:bai_no_abrev :text]]
   :file-name-regex #".*LOG_BAIRRO\.TXT"})

(def log-var-bai-table
  {:table-name :log_var_bai
   :columns [[:bai_nu :integer :primary-key]
             [:vdb_nu :integer]
             [:vdb_tx :text]]
   :file-name-regex #".*LOG_VAR_BAI\.TXT"})



(def log-faixa-bairro-table
  {:table-name :log_faixa_bairro
   :columns [[:bai_nu :integer :primary-key]
             [:fcb_cep_ini :text]
             [:fcb_cep_fim :text]]
   :file-name-regex #".*LOG_FAIXA_BAIRRO\.TXT"})

(def log-cpc-table
  {:table-name :log_cpc
   :columns [[:cpc_nu :integer :primary-key]
             [:ufe_sg :text]
             [:loc_nu :integer]
             [:cpc_no :text]
             [:cpc_endereco :text]
             [:cep :text]]
   :file-name-regex #".*LOG_CPC\.TXT"})

(def log-faixa-cpc-table
  {:table-name :log_faixa_cpc
   :columns [[:cpc_nu :integer :primary-key]
             [:cpc_inicial :text]
             [:cpc_final :text]]
   :file-name-regex #".*LOG_FAIXA_CPC\.TXT"})

(def logradouros-table
  {:table-name :logradouros
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
             [:log_no_abrev :text]]
   :file-name-regex #".*LOG_LOGRADOURO_[A-Z]{2}\.TXT"})

(def log-var-log-table
  {:table-name :log_var_log
   :columns [[:log_nu :integer :primary-key]
             [:vlo_nu :integer]
             [:tlo_tx :text]
             [:vlo_tx :text]]
   :file-name-regex #".*LOG_VAR_LOG\.TXT"})



(def log-num-sec-table
  {:table-name :log_num_sec
   :columns [[:log_nu :integer :primary-key]
             [:sec_nu_ini :text]
             [:sec_nu_fim :text]
             [:sec_in_lado :text]]
   :file-name-regex #".*LOG_NUM_SEC\.TXT"})

(def log-grande-usuario-table
  {:table-name :log_grande_usuario
   :columns [[:gru_nu :integer :primary-key]
             [:ufe_sg :text]
             [:loc_nu :integer]
             [:bai_nu :integer]
             [:log_nu :integer]
             [:gru_no :text]
             [:gru_endereco :text]
             [:cep :text]
             [:gru_no_abrev :text]]
   :file-name-regex #".*LOG_GRANDE_USUARIO\.TXT"})

(def log-unid-oper-table
  {:table-name :log_unid_oper
   :columns [[:uop_nu :integer :primary-key]
             [:ufe_sg :text]
             [:loc_nu :integer]
             [:bai_nu :integer]
             [:log_nu :integer]
             [:uop_no :text]
             [:uop_endereco :text]
             [:cep :text]
             [:uop_in_cp :text]
             [:uop_no_abrev :text]]
   :file-name-regex #".*LOG_UNID_OPER\.TXT"})



(def log-faixa-uop-table
  {:table-name :log_faixa_uop
   :columns [[:uop_nu :integer :primary-key]
             [:fnc_inicial :integer]
             [:fnc_final :integer]]
   :file-name-regex #".*LOG_FAIXA_UOP\.TXT"})

(def ect-pais-table
  {:table-name :ect_pais
   :columns [[:pai_sg :text]
             [:pai_sg_alternativa :text]
             [:pai_no_portugues :text]
             [:pai_no_ingles :text]
             [:pai_no_frances :text]
             [:pai_abreviatura :text]]
   :file-name-regex #".*ECT_PAIS\.TXT"})

(def all-tables
  [log-faixa-uf-table
   log-localidade-table
   log-var-loc-table
   log-faixa-localidade-table
   bairros-table
   log-var-bai-table
   log-faixa-bairro-table
   log-cpc-table
   log-faixa-cpc-table
   logradouros-table
   log-var-log-table
   log-num-sec-table
   log-grande-usuario-table
   log-unid-oper-table
   log-faixa-uop-table
   ect-pais-table])

