(ns gap.nio
  "`java.nio.*` interop helpers"
  (:require [clojure.java.io :as io])
  (:import [java.nio.file FileSystems Path Paths]
           [java.io Writer]))

(defn ^Path ->path
  [& segments]
  (-> (Path/of (first segments) (into-array String (rest segments)))))

(defn ^boolean starts-with?
  [^Path full ^Path prefix]
  (.startsWith full prefix))

;; The functions below mimic clojure.java.nio with the extra step(s) of converting between NIO and java.io.
;; They're provided mostly for convenience, this way just a single require is needed on caller side.

(defn ^boolean make-parents
  [^Path path]
  (io/make-parents (.toFile path)))

(defn ^Writer writer
  [^Path path]
  (io/writer (.toFile path)))
