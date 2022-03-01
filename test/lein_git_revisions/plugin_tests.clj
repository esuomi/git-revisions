(ns lein-git-revisions.plugin-tests
  (:require [clojure.test :refer :all]
            [lein-git-revisions.plugin :as plugin])
  (:import (java.time Clock LocalDate ZoneId)))

(defn- fix-clock-to
  [^long y ^long m ^long d]
  (Clock/fixed (-> (LocalDate/of y m d)
                   (.atStartOfDay (ZoneId/systemDefault))
                   .toInstant)
               (ZoneId/systemDefault)))

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
      (is (= "16" (plugin/lookup-calver :calver/d0))))))
