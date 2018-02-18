(ns omniconf.ssm
  (:require [amazonica.aws.simplesystemsmanagement :as ssm]
            [omniconf.core :as cfg]))

(defn- get-parameters
  "Fetches parameters from SSM recursively under the given `path`. Returns a map
  of keys to values, where keys are already split into vectors of keywords by
  the `separator`."
  [path separator]
  (let [parameters (ssm/get-parameters-by-path {:path path
                                                :recursive true
                                                :with-decryption true})]
    (->> (:parameters parameters)
         (map (juxt :name :value))
         (into {}))))

(defn set-value-from-ssm
  "Fetch a single value from Amazon SSM by the given `ssm-key-name` and set it in
  Omniconf by `omniconf-key`."
  [omniconf-key ssm-key-name]
  (let [value (-> (ssm/get-parameter {:name ssm-key-name :with-decryption true})
                  :parameter :value)]
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

          path (if (.endsWith path "/") path (str path "/"))
         parameters (get-parameters path "/")]
      (when-not (empty? relative-keys)
        (let [parameters (get-parameters path "/")]
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
