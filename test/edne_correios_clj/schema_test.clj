(ns edne-correios-clj.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [edne-correios-clj.db :as db]))

(deftest every-table-is-well-formed
  (doseq [[k {:keys [files columns]}] db/tables]
    (testing (str "table " k)
      (is (seq files)   ":files list is non-empty")
      (is (seq columns) ":columns list is non-empty")
      (let [[_ _ & modifiers] (first columns)]
        (is (some #{:primary-key} modifiers)
            "first column has :primary-key modifier")))))

(deftest no-filename-routes-to-more-than-one-table
  ;; If a filename appeared in two tables' :files lists, file->table would
  ;; silently collapse it to whichever table got built last — routing the
  ;; data to the wrong loader. Guard the input lists directly.
  (let [all-files (mapcat :files (vals db/tables))]
    (is (= (count all-files) (count (set all-files))))))
