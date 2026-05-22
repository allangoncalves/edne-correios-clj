(ns user
  (:require [edne-correios-clj.core :as core]
            [edne-correios-clj.db :as db]
            [next.jdbc :as jdbc]))

(comment

  (def db-name "example.db")
  (def log-dir "/tmp/edne-correios-clj/log")
  (def delta-dir "/tmp/edne-correios-clj/delta")

  (def conn (jdbc/get-connection (jdbc/get-datasource {:dbtype "sqlite" :dbname db-name})))

  (core/-main)                                              ; fetch + execute
  (core/execute db-name log-dir delta-dir)                  ; skip fetch, just run
  (db/fetch-ceps conn)

  ;; Profiling — requires the :dev alias (clj-async-profiler).
  ;; Won't load on :repl alias.
  (require '[clj-async-profiler.core :as prof])
  (prof/serve-ui 8080)
  (prof/profile {:event :alloc} (core/execute db-name log-dir delta-dir)))
