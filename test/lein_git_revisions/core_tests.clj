(ns lein-git-revisions.core-tests
  (:require [clojure.test :refer :all]
            [git-revisions.core :as plugin])
  (:import (java.time Clock ZoneId ZonedDateTime)))

(defn- fix-clock-to
  ([^Long y ^Long m ^Long d]
   (fix-clock-to y m d 0 0 0))
  ([^Long y ^Long m ^Long d ^Long h ^Long mm ^Long s]
   (-> (ZonedDateTime/of y m d h mm s 0 (ZoneId/systemDefault))
       .toInstant
       (Clock/fixed (ZoneId/systemDefault)))))

(deftest calver-lookup-formatting
  (testing "Patterns for early 21st century date"
    (with-bindings {#'plugin/*clock* (fix-clock-to 2006 2 9)}
      (is (= "2006" (plugin/lookup-calver :calver/yyyy)))
      (is (= "6" (plugin/lookup-calver :calver/yy)))
      (is (= "06" (plugin/lookup-calver :calver/y0)))
      (is (= "2" (plugin/lookup-calver :calver/mm)))
      (is (= "02" (plugin/lookup-calver :calver/m0)))
      (is (= "6" (plugin/lookup-calver :calver/ww)))
      (is (= "06" (plugin/lookup-calver :calver/w0)))
      (is (= "9" (plugin/lookup-calver :calver/dd)))
      (is (= "09" (plugin/lookup-calver :calver/d0)))))

  (testing "Patterns for mid-21st century date"
    (with-bindings {#'plugin/*clock* (fix-clock-to 2168 11 16)}
      (is (= "2168" (plugin/lookup-calver :calver/yyyy)))
      (is (= "168" (plugin/lookup-calver :calver/yy)))
      (is (= "168" (plugin/lookup-calver :calver/y0)))
      (is (= "11" (plugin/lookup-calver :calver/mm)))
      (is (= "11" (plugin/lookup-calver :calver/m0)))
      (is (= "46" (plugin/lookup-calver :calver/ww)))
      (is (= "46" (plugin/lookup-calver :calver/w0)))
      (is (= "16" (plugin/lookup-calver :calver/dd)))
      (is (= "16" (plugin/lookup-calver :calver/d0)))))

  (testing "all supported patterns can be used in format"
    (with-bindings {#'plugin/*clock* (fix-clock-to 2022 11 3 7 44 55)}
      (is (= "2022-22-22-11-11-44-44-3-03" (plugin/revision-generator {:pattern [:segment/always [:calver/yyyy "-" :calver/yy "-" :calver/y0 "-" :calver/mm "-" :calver/m0 "-" :calver/ww "-" :calver/w0 "-" :calver/dd "-" :calver/d0]]} nil nil))))))

(deftest datetime-lookup
  (testing "Most common datetime parts are supported"
    (with-bindings {#'plugin/*clock* (fix-clock-to 1991 8 24)}
      (is (= "1991" (plugin/lookup-datetime :dt/year)))
      (is (= "8" (plugin/lookup-datetime :dt/month)))
      (is (= "24" (plugin/lookup-datetime :dt/day)))
      (is (= "0" (plugin/lookup-datetime :dt/hour)))
      (is (= "0" (plugin/lookup-datetime :dt/minute)))
      (is (= "0" (plugin/lookup-datetime :dt/second)))))

  (testing "all supported patterns can be used in format"
    (with-bindings {#'plugin/*clock* (fix-clock-to 1985 10 16 6 12 0)}
      (is (= "1985.10.16T6.12.0Z" (plugin/revision-generator {:pattern [:segment/always [:dt/year "." :dt/month "." :dt/day "T" :dt/hour "." :dt/minute "." :dt/second "Z"]]} nil nil))))))
