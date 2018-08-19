(ns omniconf.ssm
  (:require [omniconf.core :as cfg])
  (:import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
           (com.amazonaws.services.simplesystemsmanagement.model
            GetParameterRequest GetParametersByPathRequest Parameter)))

(defn- get-parameters
  "Fetches parameters from SSM recursively under the given `path`. Returns a map
  of SSM keys to values."
  [path]
  (let [req (doto (GetParametersByPathRequest.)
              (.setPath path)
              (.setRecursive true)
              (.setWithDecryption true))
        resp (-> (AWSSimpleSystemsManagementClientBuilder/defaultClient)
                 (.getParametersByPath req))]
    (->> (.getParameters resp)
         (map (fn [^Parameter p] [(.getName p) (.getValue p)]))
         (into {}))))

(defn set-value-from-ssm
  "Fetch a single value from Amazon SSM by the given `ssm-key-name` and set it in
  Omniconf by `omniconf-key`."
  [omniconf-key ssm-key-name]
  (let [req (doto (GetParameterRequest.)
              (.setName ssm-key-name)
              (.setWithDecryption true))
        value (-> (AWSSimpleSystemsManagementClientBuilder/defaultClient)
                  (.getParameter req)
                  .getParameter .getValue)]
    (cfg/set omniconf-key value)))

(defn populate-from-ssm
  "Fill configuration from AWS Systems Manager. Recursively look up all parameters
  under the given `path`."
  [^String path]
  (try
    (let [scheme
          (->> (#'cfg/flatten-and-transpose-scheme :ssm @@#'cfg/config-scheme)
               (remove #(:nested (second %)))
               (group-by #(.startsWith ^String (first %) "./")))

          relative-keys (into {} (get scheme true))
          absolute-keys (into {} (get scheme false))

          path (if (.endsWith path "/") path (str path "/"))]
      (when-not (empty? relative-keys)
        (let [parameters (get-parameters path)]
          (when (and (empty? parameters) (empty? absolute-keys))
            (@@#'cfg/logging-fn "WARNING: No parameters received from SSM:" path))

          (doseq [[ssm-key spec] relative-keys
                  :let [full-key (str path (subs ssm-key 2))
                        value (get parameters full-key)]
                  :when value]
            (cfg/set (:name spec) value))))

      (doseq [[ssm-key spec] absolute-keys]
        (set-value-from-ssm (:name spec) ssm-key)))
    (catch clojure.lang.ExceptionInfo e (#'cfg/log-and-rethrow e))))
