(defproject fi.polycode/git-revisions "_"
  :description "Generate software revision strings based on Git and system context data."
  :url "https://github.com/esuomi/git-revisions"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :scm {:name "git" :url "https://github.com/esuomi/git-revisions"}

  :dependencies []

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/CLOJARS_USERNAME
                                    :password      :env/CLOJARS_TOKEN}]]

  :global-vars {*warn-on-reflection* true}

  :plugins [[fi.polycode/lein-git-revisions "LATEST"]
            [lein-pprint "1.3.2"]]

  :profiles {:dev {:dependencies [[lambdaisland/kaocha "1.66.1034"]
                                  [lambdaisland/kaocha-cloverage "1.0-45"]]}}

  :git-revisions {:format        :semver
                  :adjust        [:env/CORE_REVISIONS_ADJUSTMENT :minor]
                  :revision-file "resources/metadata.edn"}

  :aliases {"kaocha" ["run" "-m" "kaocha.runner"]})
