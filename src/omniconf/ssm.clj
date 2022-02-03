(ns omniconf.ssm
  (:require [omniconf.core :as cfg])
  (:import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
           (com.amazonaws.services.simplesystemsmanagement.model
            GetParameterRequest GetParametersByPathRequest Parameter)
           (java.util.concurrent Executors ScheduledExecutorService
                                 ThreadFactory TimeUnit)))

(defn- get-parameter
  "Fetch a single value from Amazon SSM by the given `ssm-key-name`."
  [ssm-key-name]
  (let [req (doto (GetParameterRequest.)
              (.setName ssm-key-name)
              (.setWithDecryption true))]
    (-> (AWSSimpleSystemsManagementClientBuilder/defaultClient)
        (.getParameter req)
        .getParameter)))

(defn- get-parameters
  "Fetch parameters from SSM recursively under the given `path`."
  [path]
  (loop [next-token nil, result []]
    (let [req (doto (GetParametersByPathRequest.)
                (.setPath path)
                (.setRecursive true)
                (.setNextToken next-token)
                (.setWithDecryption true))
          resp (-> (AWSSimpleSystemsManagementClientBuilder/defaultClient)
                   (.getParametersByPath req))
          result (into result (.getParameters resp))
          next-token (.getNextToken resp)]
      (if next-token
        (recur next-token result)
        result))))

(defn set-value-from-ssm
  "Fetch a single value from Amazon SSM by the given `ssm-key-name` and set it in
  Omniconf by `omniconf-key`."
  [config-comp omniconf-key ssm-key-name]
  (cfg/set config-comp omniconf-key (get-parameter ssm-key-name)))

(def ^:private ssm-params-cache (atom {}))

(defn populate-from-ssm
  "Fill configuration from AWS Systems Manager. Recursively look up all parameters
  under the given `path`. If `only-modified?` is true, it will update only those
  parameters that were modified since the last time we checked SSM."
  ([config-comp path] (populate-from-ssm config-comp path false))
  ([config-comp ^String path, only-modified?]
   (try
     (let [scheme
           (->> (#'cfg/flatten-and-transpose-scheme :ssm (:config-scheme @config-comp))
                (remove #(:nested (second %)))
                (group-by #(.startsWith ^String (first %) "./")))

           relative-keys (into {} (get scheme true))
           absolute-keys (into {} (get scheme false))

           path (if (.endsWith path "/") path (str path "/"))
           relative-params (get-parameters path)
           absolute-params (mapv get-parameter (keys absolute-keys))

           cache @ssm-params-cache
           new-cache (->> (concat relative-params absolute-params)
                          (map (fn [^Parameter p] [(.getName p) p]))
                          (into {}))
           modified-param?
           (fn [^Parameter p]
             (let [cached (get cache (.getName p))]
               (or (nil? cached) (not= (.getLastModifiedDate p)
                                       (.getLastModifiedDate ^Parameter cached)))))
           params->kv
           (fn [params]
             (->> params
                  (keep (fn [^Parameter p]
                          (when (or (not only-modified?) (modified-param? p))
                            [(.getName p) (.getValue p)])))
                  (into {})))

           relative-kv (params->kv relative-params)
           relative-kvs (for [[ssm-key spec] relative-keys
                              :let [full-key (str path (subs ssm-key 2))
                                    value (get relative-kv full-key)]
                              :when (some? value)]
                          [(:name spec) (#'cfg/parse spec value)])

           absolute-kv (params->kv absolute-params)
           absolute-kvs (for [[ssm-key spec] absolute-keys
                              :let [value (get absolute-kv ssm-key)]
                              :when (some? value)]
                          [(:name spec) (#'cfg/parse spec value)])

           values-cnt (+ (count relative-kvs) (count absolute-kvs))]

       (when (or (pos? values-cnt) (not only-modified?))
         ((:logging-fn @config-comp) (format "Populating Omniconf from AWS SSM, path %s: %s value(s)"
                                              path values-cnt)))

       (doseq [[k v] relative-kvs] (cfg/set config-comp k v))
       (doseq [[k v] absolute-kvs] (cfg/set config-comp k v))

       (reset! ssm-params-cache new-cache))
     (catch clojure.lang.ExceptionInfo e (#'cfg/log-and-rethrow config-comp e)))))

(defonce ^:private ssm-poller (atom nil))

(defn- make-scheduled-executor []
  (Executors/newSingleThreadScheduledExecutor
   (reify ThreadFactory
     (newThread [_ r] (doto (Thread. r "omniconf-ssm-polling-thread")
                        (.setDaemon true))))))

(defn populate-from-ssm-continually
  "Like `populate-from-ssm`, but runs regularly at the specified interval. Use
  this to dynamically reconfigure your program at runtime."
  [config-comp path interval-in-seconds]
  (populate-from-ssm config-comp path)
  (let [poller (or @ssm-poller (reset! ssm-poller (make-scheduled-executor)))]
    (.scheduleAtFixedRate ^ScheduledExecutorService poller
                          #(try (populate-from-ssm path true)
                                ;; Prevent exceptions from breaking the scheduler.
                                (catch Exception _))
                          interval-in-seconds interval-in-seconds TimeUnit/SECONDS)))

(defn stop-ssm-poller
  "Stop the process that polls SSM for configuration, created by
  `populate-from-ssm-continually`."
  []
  (when-let [poller @ssm-poller]
    (.shutdown ^ScheduledExecutorService poller)
    (reset! ssm-poller nil)))

#_(populate-from-ssm-continually "/foo/bar/baz" 10)
#_(stop-ssm-poller)
