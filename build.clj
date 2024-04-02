(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            clojure.tools.build.tasks.write-pom
            [org.corfield.build :as bb]))

(defmacro opts+ []
  `(let [url# "https://github.com/grammarly/omniconf"
         version# "0.5.2"]
     (-> {:lib 'com.grammarly/omniconf
          :version version#
          :tag version#
          :scm {:url url#}
          :pom-data [[:description "Configuration library for Clojure that favors explicitness"]
                     [:url url#]
                     [:licenses
                      [:license
                       [:name "Apache License, Version 2.0"]
                       [:url "http://www.apache.org/licenses/LICENSE-2.0"]]]]}
         (merge ~'opts))))

;; Hack to propagate scope into pom.
(alter-var-root
 #'clojure.tools.build.tasks.write-pom/to-dep
 (fn [f]
   (fn [[_ {:keys [mvn/scope]} :as arg]]
     (let [res (f arg)
           alias (some-> res first namespace)]
       (cond-> res
         (and alias scope) (conj [(keyword alias "scope") scope]))))))

(defn test "Run all the tests." [opts]
  (bb/clean opts)
  (bb/run-tests (cond-> (assoc opts :aliases [:ssm])
                  (:clj opts) (update :aliases conj (:clj opts))))
  opts)

(defn- build-jar [opts extra-aliases include-str]
  (bb/clean opts)
  (let [{:keys [class-dir src+dirs] :as opts} (#'bb/jar-opts opts)
        opts (assoc opts :basis (b/create-basis {:aliases extra-aliases}))]
    (b/write-pom opts)
    (b/copy-dir {:src-dirs   src+dirs
                 :target-dir class-dir
                 :include    include-str})
    (println "Building jar for" (:lib opts) "...")
    (b/jar opts)))

(defn jar-base
  "Compile and package base Omniconf jar."
  [opts]
  (let [opts (opts+)]
    (bb/clean opts)
    (build-jar opts [] "omniconf/core.clj")
    opts))

(defn jar-ssm
  "Compile and package Ominconf SSM JAR."
  [opts]
  (let [opts (-> (opts+)
                 (assoc :lib 'com.grammarly/omniconf.ssm)
                 (assoc-in [:pom-data 0 1] "Module for Omniconf to support Amazon SSM as a configuration source"))]
   (bb/clean opts)
   (build-jar opts [:ssm] "omniconf/ssm.clj")
   opts))

(defn deploy "Deploy jars to Clojars." [opts]
  (bb/deploy (jar-base opts))
  (bb/deploy (jar-ssm opts)))
