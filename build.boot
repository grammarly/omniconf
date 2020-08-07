(task-options!
 pom  {:project     'com.grammarly/omniconf
       :version     "0.4.2-SNAPSHOT"
       :description "Configuration library for Clojure that favors explicitness"
       :license     {"Apache License, Version 2.0"
                     "http://www.apache.org/licenses/LICENSE-2.0"}
       :url         "https://github.com/neuromantik33/omniconf"
       :scm         {:url "https://github.com/neuromantik33/omniconf"}})

(def clj-version (or (System/getenv "BOOT_CLOJURE_VERSION") "1.10.1"))

(def base-deps
  [['org.clojure/clojure clj-version :scope "provided"]])

(def ssm-deps
  '[[com.amazonaws/aws-java-sdk-core "1.11.802"
     :exclusions [com.fasterxml.jackson.core/jackson-databind
                  com.fasterxml.jackson.dataformat/jackson-dataformat-cbor]] ;; Use newer version explicitly due to security risks.
    [com.amazonaws/aws-java-sdk-ssm "1.11.802"]
    [com.fasterxml.jackson.core/jackson-databind "2.10.1"]
    [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.10.1"]])

(def dev-deps
  '[[boot/core "2.8.2" :scope "provided"]
    [metosin/bat-test "0.4.4" :scope "test"]])

(set-env! :dependencies (concat base-deps ssm-deps dev-deps)
          :source-paths #{"src/"}
          :test-paths #{"test/"}
          :target-path "target/")

(require 'boot.util)

(ns-unmap 'boot.user 'test)
(deftask test
  "Run unit tests."
  [j junit-path PATH str "If provided, produce JUnit XML in PATH."]
  (set-env! :source-paths #(into % (get-env :test-paths)))
  (require 'metosin.bat-test)
  (let [reporters (if junit-path
                    [:pretty {:type :junit :output-to junit-path}]
                    [:pretty])]
    ((resolve 'metosin.bat-test/bat-test) :report reporters)))

(deftask deploy
  "Build and deploy the JAR files."
  []
  (comp (sift :add-resource (get-env :source-paths)
              :include #{#"^omniconf/core.clj$"})
        (pom :dependencies base-deps)
        (jar)
        (push :repo "clojars")

        ;; Build SSM jar
        (sift :add-resource (get-env :source-paths)
              :include #{#"^omniconf/ssm.clj$"})
        (pom :project 'com.grammarly/omniconf.ssm
             :description "Module for Omniconf to support Amazon SSM as a configuration source"
             :dependencies ssm-deps)
        (jar)
        (push :repo "clojars")))
