(ns lein-git-revisions.plugin
  (:require [clojure.java.shell :refer [with-sh-dir]]
            [clojure.string :as str]
            [cuddlefish.core :as git]
            [leiningen.core.main :refer [info warn debug]])
  (:import (java.util.regex Matcher Pattern)))

(defn map->nsmap
  "Namespaces all non-namespaced keys in the given map.

  ```clojure
  (map->nsmap {:a 1 :b 2 :c/d 3} \"x\")
  => {:x/a 1, :x/b 2, :c/d 3}
  ```

  Originally from [StackOverflow Q#44523 A#43722784](https://stackoverflow.com/a/43722784/44523)"
  [m n]
  (let [namespaced
        (reduce-kv (fn [acc k v]
                     (let [new-kw (if (and (keyword? k)
                                           (not (qualified-keyword? k)))
                                    (keyword (str n) (name k))
                                    k)]
                       (assoc acc new-kw v)))
                   {}
                   m)]
    (debug "Available keys" namespaced)
    namespaced))

(defn adjust-value
  [value adjust]
  (case adjust
    :inc (inc (Integer/parseInt value))
    :clear 0
    value))

(def predefined-formats {:semver {:tag-pattern #"v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+).*$"
                                  :pattern     [:segment/when [:git/untagged? :constants/unknown]
                                                :segment/when [:git/tag :rev/major "." :rev/minor "." :rev/patch]
                                                :segment/when [:git/ahead? "-" :constants/ahead]
                                                :segment/when [:git/ref-short "+" :git/ref-short]
                                                :segment/when [:git/unversioned? "+" :constants/unversioned]]
                                  :adjustments {:major {:rev/major :inc :rev/minor :clear :rev/patch :clear}
                                                :minor {:rev/minor :inc :rev/patch :clear}
                                                :patch {:rev/patch :inc}}
                                  :constants   {:ahead       "SNAPSHOT"
                                                :unknown     "UNKNOWN"
                                                :unversioned "UNVERSIONED"}}})

(defn lookup-env
  [part]
  (when (= "env" (namespace part))
    (System/getenv (str/upper-case (name part)))))

(defn lookup-gen
  "Generates a dynamic value for supported lookup `parts`."
  [part]
  (when (= "gen" (namespace part))
    (case (name part)
      "timestamp" "2022-02-22")))

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

(defn revision-generator
  [{:keys [tag] :as git}
   format
   adjust]
  (let [git (merge git
                   (when (nil? git) {:unversioned? true})
                   (when (nil? tag) {:untagged?    true}))

        {:keys [tag-pattern pattern constants adjustments] :as config}
        (cond (keyword? format) (get predefined-formats format)
              (map? format)     format) ; TODO: else "unsupported format <blaa>"

        _                    (debug "Will use configuration " config)
        lookup               (some-fn (map->nsmap constants "constants")
                                      (map->nsmap git "git")
                                      (lookup-group (re-matcher (or tag-pattern #"$^") (or tag "")))
                                      lookup-gen
                                      lookup-env)
        adjustments          (create-adjustments adjustments lookup adjust)
        into-version-segment (resolve-and-adjust lookup adjustments)]
    (reduce
      (fn [acc [directive format]]
        (str acc (case directive
                   :segment/always (reduce into-version-segment "" format)
                   :segment/when   (when (lookup (first format))
                                     (reduce into-version-segment "" (rest format)))
                   "")))  ; TODO: what could be good default?
      ""
      (partition 2 pattern))))
; TODO: write revision file

(defn middleware
  ; TODO: some minimal default config
  [{:keys           [git-revisions root]
    :as             project}]
  (with-sh-dir root
    (let [{:keys [format adjust]} git-revisions
          git-config              {:git               "git"
                                   :describe-pattern  git/git-describe-pattern}
          git-context             (-> (git/status git-config)
                                      (dissoc :version)
                                      (assoc :branch (git/current-branch git-config)))]

  (-> project
      (assoc :version (revision-generator git-context format adjust))))))

(revision-generator {:tag "v0.0.0-alpha1" :ahead? true :ref-short "12ab34cd"}
                    :semver
                    :minor)

(revision-generator {:tag "yessir" :ahead? true :ref-short "12ab34cd"}
                    {:pattern [:segment/always [:constants/coca-cola]]
                     :constants {:coca-cola "Pepsi"}}
                    nil)
