(ns edne-correios-clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edne-correios-clj.db :as db]
            [edne-correios-clj.db-schemas :as db-schemas]
            [next.jdbc :as jdbc]))

(def ^:dynamic *batch-size* 100)

(def log-dir "./src/data/log")
(def delta-dir "./src/data/delta")

(defn with-open-xform [step]
  (fn
    ([] (step))
    ([dst] (step dst))
    ([dst x]
     (with-open [y x]
       (step dst y)))))

(defn print-reducer
  ([]
   nil)
  ([_])
  ([_ input]
   (println input)))

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

(defn ops-from-file
  [xform file-paths]
  (transduce xform
             sum-reducer
             file-paths))

(defn delta-op!
  [table-name row]
  (let [{:keys [columns]} (get db-schemas/tables table-name)
        primary-key-column-name (-> columns ffirst)
        operation (find-first #{"INS" "UPD" "DEL"} row)]
    (case operation
      ("INS" "UPD") (db/insert! table-name (->> row
                                                (remove #{"INS" "UPD" "DEL"})
                                                (take (count columns))))
      ("DEL") (db/delete-from table-name [:= primary-key-column-name (first row)]))))

(defn seed
  [table-name file-paths]
  (ops-from-file (comp (map (fn [file-path] (io/reader file-path :encoding "ISO-8859-1")))
                       with-open-xform
                       (mapcat line-seq)
                       (map #(str/split % #"\@"))
                       (partition-all *batch-size*)

                       (map (partial db/bulk-insert! table-name)))
                 file-paths))

(defn apply-delta-files
  [table-name file-paths]
  (ops-from-file (comp (map (fn [file-path] (io/reader file-path :encoding "ISO-8859-1")))
                       with-open-xform
                       (mapcat line-seq)
                       (map #(str/split % #"\@"))
                       (map (partial delta-op! table-name)))
                 file-paths))

(comment

 (doseq [[table-name {:keys [columns]}] db-schemas/tables]
   (db/create-table table-name columns))

 (doseq [[table-name files] (->> (clojure.java.io/file log-dir)
                                 file-seq
                                 (map #(.getPath %))
                                 (group-by find-table-name))
         :when (some? table-name)]
   (println table-name)
   (seed table-name files))

 (doseq [[table-name files] (->> (clojure.java.io/file delta-dir)
                                 file-seq
                                 (map #(.getPath %))
                                 (group-by find-table-name))
         :when (some? table-name)]
   (println table-name)
   (apply-delta-files table-name files)))




(defn -main [])

;; Uncomment the following line to run the -main function when executing this script
;; (-main)