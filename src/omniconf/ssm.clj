(ns omniconf.ssm
  (:require
    [cognitect.aws.client.api :as aws]
    [omniconf.core :as cfg]))

(defn get-parameters
  [path]
  (let [ssm (aws/client {:api :ssm})
        op {:op :GetParametersByPath
            :request {:Path path, :Recursive true, :WithDecryption true}}]
    (loop [parameters {}
           {:keys [Parameters NextToken]} (aws/invoke ssm op)]
      (let [parameters (reduce #(assoc %1 (:Name %2) (:Value %2))
                               parameters
                               Parameters)]
        (if NextToken
          (recur parameters
                 (aws/invoke ssm (assoc-in op [:request :NextToken] NextToken)))
          parameters)))))

(defn set-value-from-ssm
  "Fetch a single value from Amazon SSM by the given `ssm-key-name` and set it in
  Omniconf by `omniconf-key`."
  [omniconf-key ssm-key-name]
  (let [ssm (aws/client {:api :ssm})
        value (get-in (aws/invoke ssm {:op :GetParameter
                                       :request {:Name ssm-key-name,
                                                 :WithDecryption true}})
                      [:Parameter :Value])]
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
