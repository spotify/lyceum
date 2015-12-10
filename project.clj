(defproject lyceum "0.1.1-SNAPSHOT"
  :description "A riemann plugin to build and deploy modular rules."
  :url "https://github.com/spotify/lyceum"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies
  [
   [org.clojure/clojure "1.7.0"]
   [riemann/riemann "0.2.10"]
   [http-kit "2.1.16"]
   [compojure "1.1.6"]
   [javax.servlet/javax.servlet-api "3.1.0"]
   [base64-clj "0.1.1"]]
  :plugins [
    [lein-marginalia "0.7.1"]
  ]
  :java-options ["-Dlyceum.mode=test"]
  :source-path "src/"
  :java-source-paths ["src/"]
  :test-selectors {
    :default (complement :integration)
    :integration :integration
    :all (constantly true)
  }
  :main lyceum.service
)
