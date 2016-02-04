(defproject example-lein "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.grammarly/omniconf "0.2.2"]]
  :aot :all
  :main example-lein.core
  :aliases {"verify" ["run" "-m" "example-lein.core/verify"]})
