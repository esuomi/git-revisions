(ns git-revisions.core-test
  (:require [clojure.java.shell :as sh]
            [clojure.test :refer :all]
            [git-revisions.core :as core]))

(defn ^:private static-sh
  [commands]
  (fn [& args]
    (if-let [res (get commands args)]
      {:exit 0
       :out res}
      {:exit -1
       :err (str "unhandled command '" args "'")})))

(deftest built-in-semver-pattern
  (let [head-ref "985eb9ee629175cb334472d36aa9536d90789a71"
        ; clojure.java.shell/sh prepends ' to all rows in multi-row output, this is a bit weird but alas
        previous-tag "'85827e8fbf41507c23c2b052c604884909995863 (tag: v1.0.0)\n"
        current-branch "master"
        commands {core/gitcmd-current-commit head-ref
                  core/gitcmd-tree-metadata "v1.0.0-8-g985eb9ee-dirty"
                  core/gitcmd-current-branch current-branch
                  core/gitcmd-previous-tag previous-tag
                  core/gitcmd-commits "999"}]

    (testing "happy case"
      (with-redefs [sh/sh (static-sh commands)]
        (let [res (core/git-context (get-in core/predefined-formats [:semver :tag-pattern]))]
          ; current commit
          (is (= [false head-ref] ((juxt :unversioned? :ref) res)))
          ; tree metadata
          (is (= [8 true "985eb9ee" true] ((juxt :ahead :ahead? :ref-short :dirty?) res)))
          ; current branch
          (is (= current-branch (:branch res)))
          ; previous matching tag
          (is (= [false "85827e8fbf41507c23c2b052c604884909995863" "v1.0.0"] ((juxt :untagged? :tag-ref :tag) res)))
          ; commit count
          (is (= 999 (:commits res)))
          )))

    (testing "untagged repository"
      (with-redefs [sh/sh (static-sh (assoc commands core/gitcmd-previous-tag ""))]
        (let [res (core/git-context (get-in core/predefined-formats [:semver :tag-pattern]))]
          (is (= true (:untagged? res))))))))
