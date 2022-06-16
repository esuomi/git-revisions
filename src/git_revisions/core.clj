(ns git-revisions.core
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [gap.nio :as nio])
  (:import (java.util.regex Matcher)
           (java.time LocalDateTime Clock Year Month LocalDate)
           (java.time.temporal WeekFields)))

(defn map->nsmap
  "Namespaces all non-namespaced keys in the given map.

  ```clojure
  (map->nsmap {:a 1 :b 2 :c/d 3} \"x\")
  => {:x/a 1, :x/b 2, :c/d 3}
  ```

  Originally from [StackOverflow Q#44523 A#43722784](https://stackoverflow.com/a/43722784/44523)"
  [m n]
  (reduce-kv (fn [acc k v]
               (let [new-kw (if (and (keyword? k)
                                     (not (qualified-keyword? k)))
                              (keyword (str n) (name k))
                              k)]
                 (assoc acc new-kw v)))
             {}
             m))

(defn adjust-value
  [value adjust]
  (case adjust
    :inc (inc (Integer/parseInt value))
    :clear 0
    value))

(def predefined-formats {:semver {:tag-pattern #"v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+).*$"
                                  :pattern     [:segment/when     [:git/untagged? :constants/unknown]
                                                :segment/when     [:git/tag :rev/major "." :rev/minor "." :rev/patch]
                                                :segment/when-not [:env/revisions_release "-" :constants/ahead]
                                                :segment/when     [:env/revisions_prerelease "-" :env/revisions_prerelease]
                                                :segment/when     [:git/unversioned? "-" :constants/unversioned]]
                                  :adjustments {:major {:rev/major :inc :rev/minor :clear :rev/patch :clear}
                                                :minor {:rev/minor :inc :rev/patch :clear}
                                                :patch {:rev/patch :inc}}
                                  :constants   {:ahead       "SNAPSHOT"
                                                :unknown     "UNKNOWN"
                                                :unversioned "UNVERSIONED"}}
                         :commit-hash {:format {:pattern [:segment/when-not [:git/unversioned? :git/ref]
                                                          :segment/when     [:git/unversioned? "UNKNOWN"]]}}})

(defn lookup-env
  [part]
  (when (= "env" (namespace part))
    (System/getenv (str/upper-case (name part)))))

(defn- lookup-group
  "Returns a `part` `lookup function` using the provided [java.util.regex.Matcher][Matcher] as a backing source for
   the values.

  Lookups are done by converting the `part` keyword's name segment to Regular Expression
  [named group][Pattern-groupname] lookup.

  ```clojure
  (resolve-part
    :rev/numbers
    (lookup-group (re-matcher #\"(?<numbers>\\d+)\" \"abc123def\")))
  ;=> \"123\"
  ```

  [Matcher]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html#groupname
  [Pattern-groupname]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html#groupname"
  [^Matcher matcher]
  (re-find matcher)                                         ; initializes the matcher
  (fn [part]
    (when (= "rev" (namespace part))
      (try (.group matcher (name part))
           (catch IllegalStateException ise
             nil)))))

(def ^:dynamic ^Clock *clock*
  "Bindable [java.time.Clock](https://docs.oracle.com/javase/8/docs/api/java/time/Clock.html) reference mainly for
  testing purposes."
  (Clock/systemDefaultZone))

(def ^:private week-of-week-based-year (.weekOfWeekBasedYear WeekFields/ISO))

(defmulti calver-formatter (fn [pattern _] pattern))

(defmethod calver-formatter "yyyy" [_ d] (->> (Year/from d) .getValue (format "%04d")))
(defmethod calver-formatter "yy"   [_ d] (str           (-> (Year/from d) (.minusYears 2000) .getValue)))
(defmethod calver-formatter "y0"   [_ d] (format "%02d" (-> (Year/from d) (.minusYears 2000) .getValue)))
(defmethod calver-formatter "mm"   [_ d] (->> (Month/from d) .getValue str))
(defmethod calver-formatter "m0"   [_ d] (->> (Month/from d) .getValue (format "%02d")))
(defmethod calver-formatter "ww"   [_ d] (->> (.get d week-of-week-based-year) str))
(defmethod calver-formatter "w0"   [_ d] (->> (.get d week-of-week-based-year) (format "%02d")))
(defmethod calver-formatter "dd"   [_ d] (->> (.getDayOfMonth d) str))
(defmethod calver-formatter "d0"   [_ d] (->> (.getDayOfMonth d) (format "%02d" )))

(defn lookup-calver
  "[CalVer](https://calver.org/) pattern lookup, wherein the `part` is normalized to lowercase to support both
  semantically correct patterns and Clojure's keyword idioms."
  [part]
  (when (= "calver" (namespace part))
    (calver-formatter (str/lower-case (name part)) (LocalDate/now *clock*))))

(defn lookup-datetime
  [part]
  (when (= "dt" (namespace part))
    (case (name part)
      "year"   (-> (Year/now *clock*) .getValue str)
      "month"  (-> (LocalDate/now *clock*) .getMonth .getValue str)
      "day"    (-> (LocalDate/now *clock*) .getDayOfMonth str)
      "hour"   (-> (LocalDateTime/now *clock*) .getHour str)
      "minute" (-> (LocalDateTime/now *clock*) .getMinute str)
      "second" (-> (LocalDateTime/now *clock*) .getSecond str))))

(defn- resolve-part
  "Resolve a single revision `part` possibly using the provided `lookup function`. Returns either the resolved
  value as string or `nil` if resolution failed to produce a meaningful value.

  The final revision string is made up of parts where each part is either a namespaced keyword or plain string
  to guide the value lookup process. The namespace of the keyword defines the expected lookup source while the
  name part of the keyword is used as the lookup value. The value may be modified before final lookup, depending
  on the implementation of the lookup source.

  ```clojure
  (resolve-part :env/user lookup-env)
  ;=> \"esko.suomi\"
  ```"
  [part lookup]
  (str (condp #(%1 %2) part
         keyword? (lookup part)
         part)))

(defn resolve-and-adjust
  [lookup adjustments]
  (fn [acc part]
    (let [v (resolve-part part lookup)
          adjust (when adjustments (adjustments part))]
      (str acc (if (and (not (str/blank? v))
                        (not (nil? adjust)))
                 (adjust-value v adjust)
                 v
                 )))))

(defn create-adjustments
  [adjustments lookup adjust-key]
  (let [adjust      (or adjust-key [])
        adjustments (or adjustments {})]
    (->> (if-not (vector? adjust) (vector adjust) adjust)
         (map
           (fn [k]
             (adjustments (or (some-> (lookup k) keyword)
                              k))))
         (filter (complement nil?))
         first)))

(defn git-context
  [tag-pattern]
  (merge
    ; extract current commit
    (let [head-rev (sh/sh "git" "rev-parse" "HEAD")]
      (if (= 0 (:exit head-rev))
        {:unversioned? false :ref (str/trim (:out head-rev))}
        {:unversioned? true}))

    ; extract tree metadata
    (let [describe (sh/sh "git" "describe" "--tags" "--dirty" "--long")]
      (when (= 0 (:exit describe))
        (let [[_ _ ahead ref-short dirty] (re-find #"(.*)-(\d+)-g([0-9a-f]*)((-dirty)?)" (:out describe))]
          {:ahead     (Integer/parseInt ahead)
           :ahead?    (not= ahead "0")
           :ref-short ref-short
           :dirty?    (not= "" dirty)})))

    ; extract current branch
    (let [branch (sh/sh "git" "rev-parse" "--abbrev-ref" "HEAD")]
      (when (= 0 (:exit branch))
        {:branch (str/trim (:out branch))}))

    ; extract previous matching tag (if any)
    (let [tags (sh/sh "git" "--no-pager"
                      "log" "--tags" "--no-walk" "--date=iso-local" "--pretty='%H%d")]
      (if (= 0 (:exit tags))
        (reduce
          (fn [defaults r]
            (let [[_ tag-ref tags] (re-find #"^'([a-z0-9]+) \(.*tag\: (.+)\)$" r)]
              (if (and (some? tag-pattern) (re-matches tag-pattern (str/trim tags)))
                (reduced {:untagged? false :tag-ref tag-ref :tag (str/trim tags)})
                defaults)))
          {:untagged? true}
          (-> (:out tags) (str/split #"\n")))
        {:untagged? true}))

    ; extract current commit count
    (let [commits (sh/sh "git" "rev-list" "HEAD" "--count")]
      (when (= 0 (:exit commits))
        {:commits (str/trim (:out commits))}))))

(defn- write-revision-file
  [root file content]
  (let [root-path (->> (nio/->path root) .toAbsolutePath)
        target    (->> (nio/->path file)
                       (.resolve root-path)
                       .toAbsolutePath
                       .normalize)]
    ; don't allow writing outside project dir
    (when (nio/starts-with? target root-path)
      (nio/make-parents target)
      (with-open [writer (nio/writer target)]
        (binding [*out* writer]
          (prn content))))))

(defn revision-generator
  [format adjust revision-file]
  (let [{:keys [tag-pattern pattern constants adjustments] :as config}
        (cond (keyword? format) (get predefined-formats format)
              (map? format)     format) ; TODO: else "unsupported format <blaa>"

        constants            (map->nsmap constants "constants")
        git-context          (map->nsmap (git-context tag-pattern) "git")
        lookup               (some-fn constants
                                      git-context
                                      (lookup-group (re-matcher (or tag-pattern #"$^") (or (:git/tag git-context) "")))
                                      lookup-calver
                                      lookup-datetime
                                      lookup-env)
        adjustments          (create-adjustments adjustments lookup adjust)
        into-version-segment (resolve-and-adjust lookup adjustments)
        revision-string      (reduce
                               (fn [acc [directive format]]
                                 (str acc (case directive
                                            :segment/always   (reduce into-version-segment "" format)
                                            :segment/when     (when (lookup (first format))
                                                                (reduce into-version-segment "" (rest format)))
                                            :segment/when-not (when-not (lookup (first format))
                                                                (reduce into-version-segment "" (rest format)))
                                            "")))  ; TODO: what could be good default?
                               ""
                               (partition 2 pattern))]
    (when (some? revision-file)
      (write-revision-file
        (:project-root revision-file)
        (:output-path revision-file)
        (merge git-context {:revision revision-string})))
    revision-string))

