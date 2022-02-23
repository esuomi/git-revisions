(defproject lein-git-revisions "_"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :eval-in-leiningen true

  :dependencies [[me.arrdem/cuddlefish "0.1.0"]]

  :plugins [[lein-git-revisions "LATEST"]]

  :git-revisions {:format :semver
                  :adjust [:env/project_revision_adjustment :minor]})
