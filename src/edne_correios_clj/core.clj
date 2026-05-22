(ns edne-correios-clj.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [edne-correios-clj.db :as db]
            [next.jdbc :as jdbc])
  (:import (java.io FilterInputStream InputStream)
           (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.nio.charset Charset)
           (java.util.zip ZipEntry ZipInputStream)))

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

(defn find-table-name
  [file-path]
  (db/file->table (.getName (io/file file-path))))

(defn parse-delta-row
  "Pure: extract the operation marker and snapshot-shape data from a delta
   row. The op marker is at fixed position `(count columns)`. Throws on
   any op that isn't INS/UPD/DEL."
  [table-name row]
  (let [{:keys [columns]} (db/tables table-name)
        n-cols (count columns)
        op     (nth row n-cols)]
    (when-not (#{"INS" "UPD" "DEL"} op)
      (throw (ex-info "Unknown delta operation"
                      {:op op :table table-name :row row})))
    {:op     op
     :data   (vec (take n-cols row))
     :pk-col (ffirst columns)
     :pk-val (first row)}))

(defn apply-delta!
  [conn table-name [row]]
  (let [{:keys [op data pk-col pk-val]} (parse-delta-row table-name row)]
    (case op
      ("INS" "UPD") (db/insert! conn table-name data)
      "DEL"         (db/delete-from conn table-name [:= pk-col pk-val]))))

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
  "Build the SQLite DB at `db-name` from the given log/delta directories.
   Pass a non-nil `csv-out` (a path or a Writer) to also export the CEP CSV."
  ([db-name log-dir delta-dir]
   (execute db-name log-dir delta-dir nil))
  ([db-name log-dir delta-dir csv-out]
   (with-open [conn (jdbc/get-connection (jdbc/get-datasource {:dbtype "sqlite"
                                                               :dbname (or db-name ":memory:")}))]
     (db/create-tables conn)
     (ops-from-files conn log-dir db/bulk-insert! *op-batch-size*)
     (ops-from-files conn delta-dir apply-delta! 1)         ; process deltas one by one
     (when csv-out
       (with-open [writer (io/writer csv-out)]
         (write-ceps conn writer))))))

;;; ============================================================
;;; HTTP
;;; ============================================================

(def edne-url "https://www2.correios.com.br/sistemas/edne/download/eDNE_Basico.zip")

(def ^:private zip-charset
  "The eDNE zip uses legacy DOS encoding for entry names with accents
   (e.g. PDFs with Portuguese characters). Java's default UTF-8 chokes."
  (Charset/forName "Cp437"))

(defn- non-closing
  "Wrap an InputStream so close() is a no-op. Lets us nest ZipInputStreams
   without the inner closing the outer when it finishes its entry."
  [^InputStream is]
  (proxy [FilterInputStream] [is] (close [] nil)))

(defn- extract-inner!
  "Iterate one inner zip, copying Delimitado/*.TXT entries to target-dir
   (stripping the Delimitado/ prefix)."
  [^InputStream outer target-dir]
  (let [zin (ZipInputStream. (non-closing outer) zip-charset)]
    (loop []
      (when-let [^ZipEntry entry (.getNextEntry zin)]
        (let [n (.getName entry)]
          (when (and (str/starts-with? n "Delimitado/")
                     (str/ends-with? n ".TXT"))
            (let [out (io/file target-dir (subs n (count "Delimitado/")))]
              (io/make-parents out)
              (with-open [w (io/output-stream out)]
                (io/copy zin w)))))
        (recur)))))

(defn- stream-extract!
  "Read an outer zip from `body`; for each inner zip entry, dispatch its
   Delimitado/*.TXT files into log-dir or delta-dir based on its name."
  [^InputStream body log-dir delta-dir]
  (with-open [outer (ZipInputStream. body zip-charset)]
    (loop []
      (when-let [^ZipEntry entry (.getNextEntry outer)]
        (when (str/ends-with? (.getName entry) ".zip")
          (extract-inner! outer
                          (if (str/includes? (.getName entry) "Delta")
                            delta-dir log-dir)))
        (recur)))))

(defn- clear-dir! [^java.io.File dir]
  (when (.exists dir)
    (doseq [f (.listFiles dir)] (.delete f))))

(defn download-and-extract!
  "Stream eDNE zip from `url`, dropping Delimitado/*.TXT entries into
   `log-dir` and Delta/Delimitado/*.TXT entries into `delta-dir`. Both
   directories are cleared first."
  [{:keys [url log-dir delta-dir]
    :or   {url edne-url}}]
  (let [log-d  (io/file log-dir)
        delta-d (io/file delta-dir)
        client (HttpClient/newHttpClient)
        req    (-> (HttpRequest/newBuilder (URI/create url))
                   (.header "User-Agent" "edne-correios-clj")
                   .build)
        resp   (.send client req (HttpResponse$BodyHandlers/ofInputStream))
        status (.statusCode resp)]
    (when-not (= 200 status)
      (throw (ex-info "Unexpected HTTP status" {:status status :url url})))
    (clear-dir! log-d)
    (clear-dir! delta-d)
    (stream-extract! (.body resp) log-d delta-d)))

(defn -main
  "Build ./example.db from the latest eDNE zip.
   Pass a CSV path as a CLI arg to also export the flattened CEP CSV."
  [& [csv-path]]
  (let [cache-dir (io/file (System/getProperty "java.io.tmpdir") "edne-correios-clj")
        log-dir   (io/file cache-dir "log")
        delta-dir (io/file cache-dir "delta")]
    (download-and-extract! {:log-dir   (.getPath log-dir)
                            :delta-dir (.getPath delta-dir)})
    (execute "example.db" (.getPath log-dir) (.getPath delta-dir) csv-path)))
