(defproject org.clojars.clj-clapps/clj-clapps "0.4.7"
  :description "A library to create command line apps with ease and elegance"
  :url "https://github.com/rinconj/clj-clapps"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.cli "1.0.206"]]
  :plugins [[lein-cloverage "1.0.13"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]
            [lein-changelog "0.3.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.0"]]}}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"update-readme-version"
            ["shell" "sed" "-i" "-e" "s/clj-clapps \"[0-9.]*\"\\\\]/clj-clapps \"${:version}\"]/"
             "-e" "s~:mvn/version \"[0-9.]*\"~:mvn/version \"${:version}\"~g"
             "README.md"]}
  :release-tasks [["shell" "git" "diff" "--exit-code"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["changelog" "release"]
                  ["update-readme-version"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["vcs" "push"]])
