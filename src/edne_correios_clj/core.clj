(ns edne-correios-clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edne-correios-clj.db :as db]
            [edne-correios-clj.db-schemas :as db-schemas]
            [next.jdbc :as jdbc]))

(def ^:dynamic *batch-size* 100)

(def dir "./src/data/log")

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
      (-> el first ::jdbc/update-count))))

(defn maybe-table-name
  [file {:keys [file-name-regex table-name]}]
  (when (re-matches file-name-regex file)
    table-name))

(defn table-name
  [file]
  (reduce (fn [_ el]
            (when-let [table-name (maybe-table-name file el)]
              (reduced table-name)))
          nil
          db-schemas/all-tables))

(defn table-seed
  [table-name file-names]
  (time (transduce (comp
                    (map (fn [file-name] (io/reader (str dir "/" file-name) :encoding "ISO-8859-1")))
                    with-open-xform
                    (mapcat line-seq)
                    (map #(str/split % #"\@"))
                    (partition-all *batch-size*)
                    (map (partial db/bulk-insert table-name)))
                   sum-reducer
                   file-names)))

(comment

  (mapv (fn [{:keys [table-name columns]}]
          (db/create-table table-name columns))
        db-schemas/all-tables)

  (->> (clojure.java.io/file dir)
       file-seq
       (map #(.getName %))
       (group-by table-name)
       (map (fn [[table-name files]]
              (when table-name
                (table-seed table-name files))))))




(defn -main [])

;; Uncomment the following line to run the -main function when executing this script
;; (-main)