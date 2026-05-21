(ns edne-correios-clj.http
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io FilterInputStream InputStream)
           (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.nio.charset Charset)
           (java.util.zip ZipEntry ZipInputStream)))

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
