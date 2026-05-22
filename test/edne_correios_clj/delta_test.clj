(ns edne-correios-clj.delta-test
  (:require [clojure.test :refer [deftest is testing]]
            [edne-correios-clj.core :as core]
            [edne-correios-clj.db :as db]
            [matcher-combinators.test :refer [match? thrown-match?]]))

(deftest ins-and-upd-shape
  (testing "DELTA_LOG_LOCALIDADE INS row"
    (let [row ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503" "INS" nil]]
      (is (match? {:op     "INS"
                   :data   ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503"]
                   :pk-col :loc_nu
                   :pk-val "1234"}
                  (core/parse-delta-row :log_localidade row)))))

  (testing "UPD row drops the trailing CEP_ANT"
    (let [row ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503" "UPD" "12940999"]]
      (is (match? {:op   "UPD"
                   :data #(= 9 (count %))}     ;; predicate matcher: exactly n-cols items
                  (core/parse-delta-row :log_localidade row))))))

(deftest del-extracts-pk-from-position-zero
  (testing "DEL keeps full data populated; only PK actually matters downstream"
    (let [row ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503" "DEL" nil]]
      (is (match? {:op     "DEL"
                   :pk-col :loc_nu
                   :pk-val "1234"}
                  (core/parse-delta-row :log_localidade row))))))

(deftest works-across-all-six-tables
  (testing "every delta-bearing table parses an INS row correctly"
    (doseq [tk [:log_localidade :log_bairro :log_cpc :log_logradouro
                :log_grande_usuario :log_unid_oper]]
      (let [{:keys [columns]} (db/tables tk)
            n   (count columns)
            row (vec (concat ["PK-VALUE"] (repeat (dec n) nil) ["INS"]))]
        (is (match? {:op     "INS"
                     :data   #(= n (count %))
                     :pk-col (ffirst columns)
                     :pk-val "PK-VALUE"}
                    (core/parse-delta-row tk row))
            (str "table " tk))))))

(deftest throws-on-unknown-op
  (testing "ex-info is thrown with :op in ex-data"
    (let [row ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503" "REL" nil]]
      (is (thrown-match? clojure.lang.ExceptionInfo
                         {:op    "REL"
                          :table :log_localidade}
                         (core/parse-delta-row :log_localidade row)))))

  (testing "nil at the op slot also throws"
    (let [row ["1234" "SP" "Atibaia" "12940000" "1" "M" nil "Atibaia" "3504503" nil nil]]
      (is (thrown-match? clojure.lang.ExceptionInfo
                         {:op nil}
                         (core/parse-delta-row :log_localidade row))))))

(deftest position-aware-not-fooled-by-data-fields
  (testing "if a data field happens to equal 'INS', the op marker at its real position still wins"
    (let [row ["1234" "SP" "INS" "12940000" "1" "M" nil "Atibaia" "3504503" "DEL" nil]]
      (is (match? {:op     "DEL"
                   :data   ["1234" "SP" "INS" "12940000" "1" "M" nil "Atibaia" "3504503"]}
                  (core/parse-delta-row :log_localidade row))))))
