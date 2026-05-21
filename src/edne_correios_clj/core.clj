(ns edne-correios-clj.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [edne-correios-clj.db :as db]
            [edne-correios-clj.db-schemas :as db-schemas]
            [edne-correios-clj.http :as http]
            [next.jdbc :as jdbc]))

(def ^:dynamic *op-batch-size* 500)

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
  [file-path]
  (db-schemas/file->table (.getName (io/file file-path))))

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
  [conn dir op-fn batch-size]
  (doseq [[table-name files] (->> (clojure.java.io/file dir)
                                  file-seq
                                  (map #(.getPath %))
                                  (group-by find-table-name))
          :when (some? table-name)]
    (println table-name)
    (jdbc/with-transaction [tx conn]
      (transduce (comp (map (fn [file-path] (io/reader file-path :encoding "ISO-8859-1")))
                       with-open-xform
                       (mapcat line-seq)
                       (map #(str/split % #"\@" -1))
                       (map (partial replace {"" nil}))
                       (partition-all batch-size)
                       (map (fn [batch] (op-fn tx table-name batch))))
                 sum-reducer
                 files))))

(defn write-ceps
  [conn writer]
  (csv/write-csv writer [["cep" "endereco" "bairro" "cidade" "uf" "uf_nome" "complemento" "nome"]])
  (transduce
   (comp (map (juxt :cep :endereco :bairro :cidade :uf :uf_nome :complemento :nome))
         (partition-all 10000)
         (map (partial csv/write-csv writer)))
   (constantly nil)
   nil
   (db/fetch-ceps conn)))

(defn execute
  [db-name log-dir delta-dir]
  (with-open [conn (jdbc/get-connection (jdbc/get-datasource {:dbtype "sqlite"
                                                              :dbname (or db-name ":memory:")}))
              writer (io/writer "output.csv")]
    (db/create-tables conn)
    (ops-from-files conn log-dir db/bulk-insert! *op-batch-size*)
    (ops-from-files conn delta-dir delta-op! 1)   ; process deltas one by one
    (write-ceps conn writer)))

(defn -main []
  (let [cache-dir (io/file (System/getProperty "java.io.tmpdir") "edne-correios-clj")
        log-dir   (io/file cache-dir "log")
        delta-dir (io/file cache-dir "delta")]
    (http/download-and-extract! {:log-dir   (.getPath log-dir)
                                 :delta-dir (.getPath delta-dir)})
    (execute "example.db" (.getPath log-dir) (.getPath delta-dir))))