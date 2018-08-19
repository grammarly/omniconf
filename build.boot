(task-options!
 pom  {:project     'com.grammarly/omniconf
       :version     "0.3.2-SNAPSHOT"
       :description "Configuration library for Clojure that favors explicitness"
       :license     {"Apache License, Version 2.0"
                     "http://www.apache.org/licenses/LICENSE-2.0"}
       :url         "https://github.com/grammarly/omniconf"
       :scm         {:url "https://github.com/grammarly/omniconf"}})

(def clj-version (or (System/getenv "BOOT_CLOJURE_VERSION") "1.9.0"))

(set-env!
 :dependencies (-> '[[com.amazonaws/aws-java-sdk-core "1.11.237" :scope "ssm"]
                     [com.amazonaws/aws-java-sdk-ssm "1.11.237" :scope "ssm"]

                     [boot/core "2.7.2" :scope "provided"]
                     [metosin/bat-test "0.4.0" :scope "test"]]
                   (conj ['org.clojure/clojure clj-version :scope "provided"]))
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
        (pom :dependencies
             ;; Remove unnecessary deps
             (remove #(let [{:keys [project scope]} (boot.util/dep-as-map %)]
                        (or (= project 'boot/core)
                            (#{"test" "ssm"} scope)))
                     (get-env :dependencies)))
        (jar)
        (push :repo "clojars")

        ;; Build SSM jar
        (sift :add-resource (get-env :source-paths)
              :include #{#"^omniconf/ssm.clj$"})
        (pom :project 'com.grammarly/omniconf.ssm
             :description "Module for Omniconf to support Amazon SSM as a configuration source"
             :dependencies
             ;; Leave only SSM deps
             (filter #(let [{:keys [scope]} (boot.util/dep-as-map %)]
                        (= scope "ssm"))
                     (get-env :dependencies)))
        (jar)
        (push :repo "clojars")))
