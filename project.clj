(defproject fi.polycode/lein-git-revisions "0.1.0-SNAPSHOT"
  :description "Automatically control Leiningen project version based on Git metadata."
  :url "https://github.com/esuomi/lein-git-revisions"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :eval-in-leiningen true

  :dependencies [[me.arrdem/cuddlefish "0.1.0"]]

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/CLOJARS_USERNAME
                                    :password      :env/CLOJARS_TOKEN}]]

  :plugins [#_[fi.polycode/lein-git-revisions "LATEST"]
            [lein-pprint "1.3.2"]]

  :git-revisions {:format :semver
                  :adjust [:env/lein_revisions_adjustments :minor]})
