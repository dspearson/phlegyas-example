(defproject phlegyas-example "0.0.1-SNAPSHOT"
  :description "an example 9P filesystem server"
  :license {:name "ISC"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [aleph "0.4.6"]
                 [phlegyas "0.1.5"]]
  :main ^:skip-aot phlegyas-example.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
