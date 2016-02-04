(defproject com.grammarly/omniconf "0.2.1"
  :description "Fancy configuration library for your Clojure programs"
  :url "https://github.com/grammarly/omniconf"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :plugins [[lein-cloverage "1.0.6"]
                             [test2junit "1.2.1"]]
                   :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}})
