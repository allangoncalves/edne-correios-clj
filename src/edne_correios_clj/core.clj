(ns edne-correios-clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edne-correios-clj.db :as db]
            [edne-correios-clj.db-schemas :as db-schemas]
            [clojure.data.csv :as csv]
            [clj-memory-meter.core :as mm]
            [next.jdbc :as jdbc]))

(def ^:dynamic *op-batch-size* 100)

(def log-dir "./src/data/log")
(def delta-dir "./src/data/delta")

(defn with-open-xform [rf]
  (fn
    ([] (rf))
    ([result] (rf result))
    ([result input]
     (with-open [y input]
       (rf result y)))))

(defn sum-reducer
  ([]
   0)
  ([sum]
   (println "Changed records: " sum))
  ([acc el]
   (+ acc
      (-> el first ::jdbc/update-count abs))))

(defn find-first
  [pred-fn coll]
  (reduce (fn [_ elem]
            (when (pred-fn elem)
              (reduced elem)))
          nil
          coll))

(defn find-table-name
  [file]
  (reduce (fn [_ [k v]]
            (when (-> v :file-name-regex (re-matches file))
              (reduced k)))
          nil
          db-schemas/tables))

(defn delta-op!
  [conn table-name [row]]
  (let [{:keys [columns]} (get db-schemas/tables table-name)
        primary-key-column-name (-> columns ffirst)
        operation (find-first #{"INS" "UPD" "DEL"} row)]
    (case operation
      ("INS" "UPD") (db/insert! conn table-name (->> row
                                                     (remove #{"INS" "UPD" "DEL"})
                                                     (take (count columns))))
      ("DEL") (db/delete-from conn table-name [:= primary-key-column-name (first row)]))))

(defn ops-from-files
  [dir op-fn batch-size]
  (doseq [[table-name files] (->> (clojure.java.io/file dir)
                                  file-seq
                                  (map #(.getPath %))
                                  (group-by find-table-name))
          :when (some? table-name)]
    (println table-name)
    (transduce (comp (map (fn [file-path] (io/reader file-path :encoding "ISO-8859-1")))
                     with-open-xform
                     (mapcat line-seq)
                     (map #(str/split % #"\@"))
                     (map (partial replace {"" nil}))
                     (partition-all batch-size)
                     (map (partial op-fn table-name)))
               sum-reducer
               files)))

(defn write-ceps-csv
  [ceps]
  (with-open [writer (io/writer "output.csv")]
    (csv/write-csv writer
                   (cons ["cep" "endereco" "bairro" "cidade" "uf" "uf_nome"]
                         ceps))))

(defn run*
  [conn]
  (db/create-tables conn)
  (ops-from-files log-dir (partial db/bulk-insert! conn) *op-batch-size*)
  (ops-from-files delta-dir (partial delta-op! conn) 1)
  (db/create-cep-view conn)
  (write-ceps-csv (db/fetch-ceps conn)))

(defn run
  []
  (with-open [conn (jdbc/get-connection (jdbc/get-datasource {:dbtype "sqlite" :dbname ":memory:"}))]
    (run* conn)))

(defn -main []
  (run))

(comment

 (require '[clj-memory-meter.core :as mm]
          '[clj-async-profiler.core :as prof])
 #_(mm/measure (-main))
 (-main)

 (prof/profile {:event :alloc} (-main))

 (prof/serve-ui 8080))