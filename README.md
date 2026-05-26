# edne-correios-clj

Um script simples para construir um banco SQLite de CEPs do Brasil localmente, a partir do [eDNE Básico dos Correios](https://www2.correios.com.br/sistemas/edne/).

## Para que serve

Os Correios disponibilizam gratuitamente o **eDNE Básico**, um conjunto de arquivos delimitados com todos os CEPs do país, atualizado quinzenalmente. Este repositório é um exemplo funcional, de pouco código, que:

1. Baixa o zip mais recente do eDNE diretamente do site dos Correios
2. Descompacta os arquivos `.TXT` de snapshot e delta em memória
3. Carrega os dados em um SQLite 
4. (Opcional) gera um arquivo CSV com os dados mais relevantes da base

## Para quem é

Este repositório **não é uma biblioteca**. Não há `clojars`, `lein install` ou API estável. É um exemplo deliberadamente curto, pensado para que você possa **copiar, colar e adaptar** ao seu projeto.

## Requisitos

- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Java 11+ (usa `java.net.http` da JDK; sem dependências HTTP externas)

## Como usar

### Clojure CLI

**Construir o DB** (`seed`):

```bash
clojure -X:seed
```

Baixa o zip mais recente, descompacta, cria `./example.db` (SQLite com 6 tabelas, ~130 MB).

Para customizar o caminho do DB:

```bash
clojure -X:seed :db-path '"meu-banco.db"'
```

**Exportar CSV de um DB existente** (`export-csv`):

```bash
clojure -X:export-csv
```

Lê `./example.db` e escreve `./output.csv` (~120 MB, ~1,5 milhão de linhas). Não baixa nada nem refaz a carga.

Para customizar:

```bash
clojure -X:export-csv :db-path '"meu-banco.db"' :csv-path '"meu-csv.csv"'
```

### Via REPL — uso interativo

Para usar o script a partir do REPL, abra com o alias `:repl`:

```bash
clojure -M:repl
```

Esse alias adiciona `dev/` ao classpath, o que carrega automaticamente o `user.clj` com `core`, `db` e `jdbc` já disponíveis. Sem dependências extras.

> Para **mexer no script** (profiling, clojure-lsp), use `clojure -M:dev` — esse alias inclui `clj-async-profiler`, `clj-memory-meter` e `clojure-lsp`.

Dentro do REPL:

```clojure
(require '[clojure.java.io :as io]
         '[edne-correios-clj.core :as core]
         '[edne-correios-clj.db   :as db]
         '[next.jdbc :as jdbc])

;; Construir o DB (equivalente a `clojure -X:seed`):
(core/seed {})
(core/seed {:db-path "meu-banco.db"})

;; Exportar o CSV de um DB existente (equivalente a `clojure -X:export-csv`):
(core/export-csv {})
(core/export-csv {:csv-path "meu-csv.csv"})

;; Só a carga, sem baixar — usando diretórios já populados.
;; Aqui o chamador é dono da connection, então dá pra compor:
(with-open [conn (jdbc/get-connection
                  (jdbc/get-datasource {:dbtype "sqlite" :dbname "example.db"}))]
  (core/execute conn
                "/tmp/edne-correios-clj/log"
                "/tmp/edne-correios-clj/delta"))

;; Consultar um example.db já gerado:
(def conn (jdbc/get-connection
           (jdbc/get-datasource {:dbtype "sqlite" :dbname "example.db"})))

;; Consultar o DB direto:
(jdbc/execute! conn ["SELECT cep, ufe_sg, log_no FROM log_logradouro LIMIT 3"])
;; => [#:log_logradouro{:cep "69918703" :ufe_sg "AC" :log_no "Nelson Mesquita"}
;;     #:log_logradouro{:cep "69911204" :ufe_sg "AC" :log_no "Tião Natureza"}
;;     #:log_logradouro{:cep "69901106" :ufe_sg "AC" :log_no "Aquários"}]
```

### Customizando

Quase tudo o que você vai querer mexer fica em **`src/edne_correios_clj/db.clj`**:

- **`tables`** — schema das tabelas que serão criadas. Adicione tabelas auxiliares (`log_var_*`, `log_faixa_*`, `ect_pais`, etc.) se precisar — os arquivos já estão dentro do zip baixado.
- **`ceps-sql`** — a query que materializa o CSV de saída. As sete branches do `UNION` cobrem cada origem possível de CEP (logradouro com/sem tipo, localidade simples, sub-localidade, CPC, grande usuário, unidade operacional).

O `core.clj` fica com a orquestração: download, parsing dos arquivos, transações por tabela, escrita do CSV.

## Testes

```bash
clojure -M:test
```

## Estrutura do código

```
.
├── deps.edn                                  # dependências: next.jdbc + sqlite-jdbc + data.csv
├── src/edne_correios_clj/
│   ├── core.clj                              # orquestração + HTTP/unzip + pipeline + CSV
│   └── db.clj                                # schema + SQL builders + query CEP
├── dev/user.clj                              # helpers de REPL (profiler etc.)
└── test/
    ├── edne_correios_clj/*.clj               # 5 arquivos de testes
    └── fixtures/edne-mini/                   # dados pequenos para o e2e
```

## Limitações conhecidas

- O **eDNE Básico** não inclui faixas numéricas de logradouros (essas estão só no eDNE Master, pago). Para a maioria dos casos ("qual cidade/bairro responde por este CEP?") o Básico já basta.
- A query principal usa apenas 6 das tabelas do eDNE. Os arquivos `LOG_VAR_*`, `LOG_FAIXA_*` e `ECT_PAIS` ficam de fora — se quiser usá-los (busca por nomes antigos, validação por faixa de CEP), adicione as entradas correspondentes em `db/tables`.
- Encoding: o eDNE usa **ISO-8859-1** nos arquivos `.TXT`. O loader respeita isso; o CSV de saída é **UTF-8**.

## Licença

Copyright © 2025-2026 Allan Gonçalves

Distribuído sob a [licença MIT](https://opensource.org/licenses/MIT).

Os dados do eDNE Básico são propriedade dos Correios e disponibilizados gratuitamente. Este repositório contém apenas código de exemplo, sem dados — você é responsável por aceitar os termos dos Correios ao baixar.
