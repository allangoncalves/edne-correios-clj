(ns edne-correios-clj.query-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [edne-correios-clj.db :as db]
            [matcher-combinators.matchers :refer [embeds]]
            [matcher-combinators.test     :refer [match?]]
            [next.jdbc :as jdbc]))

;; Each CEP query branch verified independently with a minimal seeded DB.
;; Assertions use matcher-combinators: nested maps inside embeds means
;; "the result vector contains at least one row matching these fields".

(def ^:dynamic *conn* nil)

(defn with-fresh-db [t]
  (with-open [conn (jdbc/get-connection (jdbc/get-datasource
                                         {:dbtype "sqlite" :dbname ":memory:"}))]
    (db/create-tables conn)
    (binding [*conn* conn] (t))))

(use-fixtures :each with-fresh-db)

(defn fetch-ceps-as-vec [conn]
  (into [] (map #(into {} %)) (db/fetch-ceps conn)))

(defn seed-base! [conn]
  (db/bulk-insert! conn :log_localidade [["100" "SP" "São Paulo" nil "1" "M" nil "São Paulo" "3550308"]])
  (db/bulk-insert! conn :log_bairro     [["200" "SP" "100" "Centro" "Ctr"]]))

(deftest branch-1-logradouro-with-tlo
  (testing "log_sta_tlo='S' → endereco is CONCAT(tlo_tx, ' ', log_no)"
    (seed-base! *conn*)
    (db/bulk-insert! *conn* :log_logradouro
                     [["300" "SP" "100" "200" nil "Voluntários da Pátria" nil "02011000" "Rua" "S" "R Vol Pátria"]])
    (is (match? (embeds [{:cep      "02011000"
                            :endereco "Rua Voluntários da Pátria"
                            :bairro   "Centro"
                            :cidade   "São Paulo"
                            :uf       "SP"
                            :uf_nome  "São Paulo"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-2-logradouro-without-tlo
  (testing "log_sta_tlo='N' → endereco is just log_no, no prefix"
    (seed-base! *conn*)
    (db/bulk-insert! *conn* :log_logradouro
                     [["301" "SP" "100" "200" nil "Antonio Carlos" nil "01310010" "Avenida" "N" "Av A Carlos"]])
    (is (match? (embeds [{:cep      "01310010"
                            :endereco "Antonio Carlos"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-3-locality-alone
  (testing "locality with own cep + no loc_nu_sub → emits one minimal row"
    (db/bulk-insert! *conn* :log_localidade
                     [["110" "RJ" "Niterói" "24000000" "0" "M" nil "Niterói" "3303302"]])
    (is (match? [{:cep         "24000000"
                  :endereco    ""
                  :bairro      ""
                  :cidade      "Niterói"
                  :uf          "RJ"
                  :uf_nome     "Rio de Janeiro"
                  :complemento ""
                  :nome        ""}]
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-4-locality-self-join
  (testing "sub-locality (loc_nu_sub set) joins back to parent"
    (db/bulk-insert! *conn* :log_localidade
                     [["120" "MG" "Belo Horizonte" nil "1" "M" nil "BH" "3106200"]
                      ["121" "MG" "Venda Nova" "31600000" "2" "D" "120" "Venda Nova" "3106200"]])
    (is (match? (embeds [{:cep     "31600000"
                            :bairro  "Venda Nova"
                            :cidade  "Belo Horizonte"
                            :uf_nome "Minas Gerais"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-5-cpc
  (testing "CPC → nome=cpc_no, endereco=cpc_endereco"
    (db/bulk-insert! *conn* :log_localidade
                     [["130" "PR" "Curitiba" nil "1" "M" nil "Curitiba" "4106902"]])
    (db/bulk-insert! *conn* :log_cpc
                     [["400" "PR" "130" "Caixa Postal Centro" "R. XV de Novembro" "80020000"]])
    (is (match? (embeds [{:cep      "80020000"
                            :endereco "R. XV de Novembro"
                            :bairro   ""
                            :cidade   "Curitiba"
                            :nome     "Caixa Postal Centro"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-6-grande-usuario
  (testing "grande_usuario → nome=gru_no, endereco=gru_endereco"
    (seed-base! *conn*)
    (db/bulk-insert! *conn* :log_grande_usuario
                     [["500" "SP" "100" "200" nil "Banco Central" "Av Paulista 1000" "01310100" "BC"]])
    (is (match? (embeds [{:cep      "01310100"
                            :endereco "Av Paulista 1000"
                            :bairro   "Centro"
                            :nome     "Banco Central"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest branch-7-unid-oper
  (testing "unid_oper → nome=uop_no, endereco=uop_endereco"
    (seed-base! *conn*)
    (db/bulk-insert! *conn* :log_unid_oper
                     [["600" "SP" "100" "200" nil "Agência Central" "R. Boa Vista 100" "01014000" "S" "AC"]])
    (is (match? (embeds [{:cep      "01014000"
                            :endereco "R. Boa Vista 100"
                            :nome     "Agência Central"}])
                (fetch-ceps-as-vec *conn*)))))

(deftest uf-nome-resolves-via-case
  (testing "the CASE expression maps every UF to a full state name"
    (db/bulk-insert! *conn* :log_localidade
                     [["140" "RR" "Boa Vista" "69300000" "0" "M" nil "Boa Vista" "1400100"]
                      ["141" "ES" "Vitória"   "29000000" "0" "M" nil "Vitória"   "3205309"]
                      ["142" "DF" "Brasília"  "70000000" "0" "M" nil "Brasília"  "5300108"]])
    (is (match? (embeds [{:uf "RR" :uf_nome "Rorâima"}
                           {:uf "ES" :uf_nome "Espírito Santo"}
                           {:uf "DF" :uf_nome "Distrito Federal"}])
                (fetch-ceps-as-vec *conn*)))))
