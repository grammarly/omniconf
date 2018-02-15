(task-options!
 pom  {:project     'com.grammarly/omniconf
       :version     "0.3.0-SNAPSHOT"
       :description "Configuration library for Clojure that favors explicitness"
       :license     {"Apache License, Version 2.0"
                     "http://www.apache.org/licenses/LICENSE-2.0"}
       :url         "https://github.com/grammarly/omniconf"
       :scm         {:url "https://github.com/grammarly/omniconf"}})

(def clj-version (or (System/getenv "BOOT_CLOJURE_VERSION") "1.9.0"))

(set-env!
 :dependencies `[[org.clojure/clojure ~clj-version :scope "provided"]
                 [boot/core "2.7.2" :scope "provided"]
                 [metosin/bat-test "0.4.0" :scope "test"]]
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

(deftask build
  "Build the JAR file."
  []
  (set-env! :resource-paths (get-env :source-paths)
            ;; Remove unnecessary deps
            :dependencies
            (fn [deps]
              (remove #(let [{:keys [project scope]} (boot.util/dep-as-map %)]
                         (or (= project 'boot/core)
                             (= scope "test")))
                      deps)))
  (comp (pom) (jar)))
