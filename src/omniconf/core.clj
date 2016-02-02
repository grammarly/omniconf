(ns omniconf.core
  "Omniconf is an exhaustive configuration tool."
  (:refer-clojure :exclude [get set])
  (:require [clojure.core :as clj]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [omniconf.core :as cfg])
  (:import java.io.File))

;; Plumbing

(def ^:private config-scheme
  "Stores configuration description of the program."
  (atom nil))

(def ^:private config-values
  "Stores current configuration values."
  (atom nil))

(def ^:private logging-fn
  "Function that is called to print debugging information and errors."
  (atom println))

(defn set-logging-fn
  "Change `println` to a custom logging function that Omniconf will use."
  [fn]
  (reset! logging-fn fn))

(defn- quit-or-rethrow
  "If `quit-on-error` is true, log the exception and exit 1 the application,
  otherwise rethrow it."
  [e quit-on-error]
  (if quit-on-error
    (do (binding [*out* *err*] (@logging-fn "ERROR:" (.getMessage e)))
        (System/exit 1))
    (throw e)))

(defn fail
  "Throws an exception with a message specified by `fmt` and `args`."
  [fmt & args]
  (throw (ex-info (apply format fmt args) {})))

;; Parsing

(defn parse-edn
  "Calls `clojure.edn/read-string` on a string."
  [s]
  (edn/read-string s))

(defn parse-number
  "Parses string as a Long."
  [s]
  (Long/parseLong s))

(defn parse-boolean
  "Parses string as a Boolean."
  [s]
  (not (#{nil "" "0" "false"} s)))

(defn parse-filename
  "Parses string as a relative filename."
  [s]
  (io/file s))

(defn parse-directory
  "Parses string as a relative directory."
  [s]
  (io/file s))

(def ^:private default-types
  "A map of standard types to their parsers and type checkers."
  {:string {:parser identity, :checker string?}
   :keyword {:parser keyword, :checker keyword?}
   :number {:parser parse-number, :checker number?}
   :boolean {:parser parse-boolean, :checker (partial instance? Boolean)}
   :file {:parser parse-filename, :checker (partial instance? File)}
   :edn {:parser parse-edn, :checker (constantly true)}})

(defn- parse
  "Given an option spec and the string value, tries to parse a the
  value. Source should be either `:env`, `:cli` or `:file`."
  [spec value-str source]
  (let [parser (if (:nested spec)
                 #(let [value (edn/read-string %)]
                    (if (map? value)
                      value
                      (fail "%s : Value of nested option should be a map, instead '%s'."
                            (:name spec) value)))
                 (or (:parser spec)
                     (get-in default-types [(:type spec) :parser])
                     identity))]
    (try (parser value-str)
         (catch clojure.lang.ExceptionInfo e (throw e))
         (catch Exception e (fail "%s : Couldn't parse value '%s'."
                                  (:name spec) value-str)))))

(defn get
  "Get the value from the current configuration given the path in nested
  structure specified by `ks`. Path can be provided as a single sequence, or as
  a variable number of keywords."
  [& ks]
  (let [ks (if (sequential? (first ks)) (first ks) ks)
        value (clj/get-in @config-values ks)]
    (if (delay? value)
      (let [calc-value (force value)]
        (swap! config-values assoc-in ks calc-value)
        calc-value)
      value)))

(defn set
  "Set the `value` for the `ks` path in the current configuration. Path can be
  provided as a single sequence, or as a variable number of keywords. If value
  is a string, call the respective parser on it before setting."
  {:forms '([& ks value] [ks-vec value])}
  [& args]
  (let [[ks value] (if (sequential? (first args))
                     ((juxt first second) args)
                     ((juxt butlast last) args))
        special-action (cond
                         (:merge (meta value)) merge
                         (:concat (meta value)) #(seq (concat %1 %2)))
        dt (:delayed-transform (get-in @config-scheme ks))
        new-value (if special-action
                    (special-action (get ks) value)
                    value)]
    (swap! config-values assoc-in ks (if dt
                                       (delay (dt new-value))
                                       new-value))))

(defmacro with-options
  "Binds given symbols to respective configuration parameters and executes
  `body` in that context. Works only for top-level parameters."
  [bindings & body]
  `(let [~@(mapcat (fn [sym] [sym `(get ~(keyword sym))]) bindings)]
     ~@body))

#_(with-options [dest results-path]
    (+ dest 3))

(defn define
  "Declare the configuration that the program supports. `scheme` is a map of
  keyword names to specs.

  Example:

  (define {:boolean-option {:description \"can be either true or nil\"}
           :string-option  {:type :string
                            :description \"this option's value is taken as is\"}
           :integer-option {:type :number
                            :required true
                            :description \"parsed as integer, must be present\"}}

  Supported attributes:

  :description - string to describe the option in the help message.
  :type - one of #{:string :keyword :number :boolean :edn :file}.
  :parser - 1-arity fn to be called on a string given by CLI options or env.
            Unnecessary if :type is specified.
  :required - boolean value whether the option must have a value.
  :required-if - 0-arity fn, if returns true, the option must have a value.
  :one-of - a set of accepted values for the given option.
  :verifier - fn on key and val, should throw an exception if option is invalid.
  :secret - if true, value will not be printed during verification.
  :nested - allows to create hierarchies of options."
  [scheme]
  (reset! config-values (sorted-map))

  ;; Recursively update scheme.
  (letfn [(walk [prefix coll]
            (->> coll
                 (map (fn [[kw-name spec]]
                        [kw-name (-> spec
                                     (assoc :name kw-name)
                                     (update-in [:env-name]
                                                #(->> (conj prefix (or % kw-name))
                                                      (map (fn [x] (.replace (.toUpperCase (name x)) "-" "_")))
                                                      (str/join "__")))
                                     (update-in [:opt-name]
                                                #(->> (conj prefix (or % kw-name))
                                                      (map name)
                                                      (str/join ".")
                                                      (str "--")))
                                     (update-in [:nested]
                                                #(when % (walk (conj prefix kw-name) %))))]))
                 (into (sorted-map))))]
    (reset! config-scheme (walk [] scheme)))

  ;; Fill default values.
  (let [walk (fn walk [prefix coll]
               (doseq [[kw-name spec] coll]
                 (when-let [default (:default spec)]
                   (apply set (conj prefix kw-name default)))
                 (when-let [nested (:nested spec)]
                   (walk (conj prefix kw-name) nested))))]
    (walk [] @config-scheme)))

(defn- flatten-and-transpose-scheme
  "Returns a flat hashmap from scheme where nested specs are in the top level,
  and keys are the string values from `:env-name` or `:opt-name`. Inside specs
  `:name` is transformed into a vector of keywords - path to that option. Source
  is `:env`, `:cli`, or `:kw`."
  [source scheme]
  (letfn [(fats [prefix scheme]
            (->> scheme
                 (mapcat (fn [[_ spec]]
                           (let [spec (update-in spec [:name] #(conj prefix %))
                                 key ((case source
                                        :env :env-name
                                        :cli :opt-name
                                        :kw :name) spec)]
                             (if-let [nested (:nested spec)]
                               (cons [key spec]
                                     (fats (:name spec) nested))
                               [[key spec]]))))
                 (into {})))]
    (fats [] scheme)))

(defn populate-from-env
  "Fill configuration from environment variables. This function must be called
  only after `define`. If `quit-on-error` is true, immediately quit when program
  occurs."
  ([] (populate-from-env false))
  ([quit-on-error]
   (try
     (doseq [[env-name spec] (flatten-and-transpose-scheme :env @config-scheme)]
       (when-let [value (clj/get (System/getenv) env-name)]
         (set (:name spec) (parse spec value :env))))
     (catch clojure.lang.ExceptionInfo e (quit-or-rethrow e quit-on-error)))))

(defn- print-cli-help
  "Prints a help message describing all supported CLI options."
  []
  (println "Stub!")
  (doseq [[_ v] @config-scheme]
    (println (format "%s - %s" (:opt-name v) (:description v)))))

(defn populate-from-opts
  "Fill configuration from command-line options."
  ([cli-opts] (populate-from-opts cli-opts false))
  ([cli-opts quit-on-error]
   (let [grouped-opts
         (loop [[c & r] (conj (vec cli-opts) ::end) curr-opt nil, result []]
           (cond (= c ::end) (if curr-opt
                               (conj result [curr-opt true])
                               result)
                 (.startsWith c "--") (recur r c (if curr-opt
                                                   (conj result [curr-opt true])
                                                   result))
                 curr-opt (recur r nil (conj result [curr-opt c]))
                 :else (fail "Malformed command-line arguments, key expected, '%s' found."
                             c)))]
     (when (clj/get (into {} grouped-opts) "--help")
       (print-cli-help)
       (System/exit 0))

     (try (let [transposed-scheme (flatten-and-transpose-scheme :cli @config-scheme)]
            (doseq [[k v] grouped-opts]
              (if-let [spec (clj/get transposed-scheme k)]
                (set (:name spec) (parse spec v :cli))
                (@logging-fn "WARNING: Unrecognized option:" k))))
          (catch clojure.lang.ExceptionInfo e (quit-or-rethrow e quit-on-error))))))

(defn populate-from-file
  "Fill configuration from an edn file."
  ([edn-file] (populate-from-file edn-file false))
  ([edn-file quit-on-error]
   (try (with-open [in (java.io.PushbackReader. (io/reader edn-file))]
          (letfn [(walk [prefix tree]
                    (doseq [[key value] tree]
                      (let [path (conj prefix key)
                            spec (get-in @config-scheme path)]
                        (if (:nested spec)
                          (walk path value)
                          (set path (if (and (string? value) (:parser spec))
                                      (parse spec value :file)
                                      value)))) tree))]
            (walk [] (edn/read in))))
        (catch clojure.lang.ExceptionInfo e (quit-or-rethrow e quit-on-error)))))

(defn report-configuration
  "Prints the current configuration state to `*out*`. Hide options marked as
  `:secret`."
  []
  (@logging-fn "Omniconf configuration:\n"
   (with-out-str
     (pprint
      (reduce (fn [values-map [val-path val-spec]]
                (if (and (:secret val-spec) (get-in values-map val-path))
                  (assoc-in values-map val-path '<SECRET>)
                  values-map))
              @config-values
              (flatten-and-transpose-scheme :kw @config-scheme))))))

(defn verify
  "Checks if all the required options are provided, if all values are in range,
  and prints the configuration. If `:quit-on-error` is set, script will exit if
  configuration is incorrect. If `:silent` is true, don't print the
  configuration state."
  [& {:keys [quit-on-error silent]}]
  (swap! config-scheme dissoc :help) ;; Not needed anymore.
  (try (doseq [[_ {kw-name :name :as spec}] (flatten-and-transpose-scheme :env @config-scheme)]
         (let [value (get-in @config-values kw-name)]
           ;; Not using `cfg/get` above to avoid forcing delays too early.
           (when (and (:required spec)
                      (nil? value))
             (fail "%s : Value for this option must be provided." kw-name))
           (when-let [r-if (:required-if spec)]
             (when (and (r-if)
                        (nil? value))
               (fail "%s : Value for this option must be provided." kw-name)))
           (when-let [one-of (:one-of spec)]
             (when-not (clj/get (clj/set one-of) value)
               (fail "%s : Value for this option is %s, but must be one of %s"
                     kw-name value one-of)))
           (when value
             (when-let [type (:type spec)]
               (when-not (or (:delayed-transform spec)
                             ((get-in default-types [type :checker]) value))
                 (fail "%s : Value for this option is %s, but must have type %s"
                       kw-name value type)))
             (when-let [verifier (:verifier spec)]
               (verifier kw-name value)))))
       (catch clojure.lang.ExceptionInfo e (quit-or-rethrow e quit-on-error)))
  (when-not silent (report-configuration)))

;; (define {:boolean-option {:description "can be either true or nil"}
;;          :string-option  {:parser identity
;;                           :description "this option's value is taken as is"}
;;          :integer-option {:parser parse-number
;;                           :required true
;;                           :description "parsed as integer, must be present"}})

;; (define {:help {:description "prints this help message"
;;                 :program-name "my-script"
;;                 :program-description "program description"}
;;          :boolean-option {:description "can be either t or nil, doesn't need a value in CLI options"}
;;          :string-option  {:parser identity
;; ;                          :required true
;;                           :description "this option's value is taken as is"}
;;          :integer-option {:parser parse-number
;;                           ;; :required true
;;                           ;; :one-of [17 42 87]
;;                           :required-if #(:string-option @config-values)
;;                           :description "parsed as integer"}

;;          ;; :compound-option
;;          ;; {:nested {:inside-option-1 {:parser identity}
;;          ;;           :inside-option-2  {:parser cfg/parse-number}}}

;;          ;; :edn-option           {:parser cfg/read-edn
;;          ;;                        :description "read as EDN structure"}
;;          ;; :file-option          {:parser cfg/parse-filename
;;          ;;                        :description "read as filename"}
;;          ;; :directory-option     {:parser cfg/parse-directory
;;          ;;                        :description "read as directory name"}
;;          ;; :option-with-default  {:parser cfg/parse-number
;;          ;;                        :default 1024
;;          ;;                        :description "has a default value"}
;;          ;; :required-option      {:parser identity
;;          ;;                        :required true
;;          ;;                        :description "must have a value before call to (CFG:VERIFY), otherwise fails"}
;;          ;; :option-from-set      {:parser keyword
;;          ;;                        :one-of # {:foo :bar :baz}
;;          ;;                        :description "value must be a member of the provided set"}
;;          ;; :existing-file-option {:parser cfg/parse-filename
;;          ;;                        :verifier cfg/verify-file-exists
;;          ;;                        :description "file should exist"}
;;          ;; :nonempty-dir-option  {:parser cfg/parse-directory
;;          ;;                        :verifier cfg/verify-directory-non-empty
;;          ;;                        :description "directory must have files"}
;;          ;; :delayed-option       {:parser cfg/parse-number
;;          ;;                        :delayed-transform (fn [v] (Thread/sleep 1000) (+ v 5))
;;          ;;                        :description "has a custom transform that is called the first time the option is read"}
;;          ;; :renamed-option       {:env-name "MY_OPTION"
;;          ;;                        :opts-name "custom-option"
;;          ;;                        :description "has custom names for different sources"}
;;          ;; :conf-file            {:parser cfg/parse-filename
;;          ;;                        :verifier cfg/verify-file-exists
;;          ;;                        :description "you can provide an additional configuration file just as another option"}
;;          })

(defn verify-file-exists
  "Check if file or directory denoted by `file` exists, raise error otherwise."
  [key file]
  (when-not (.exists file)
    (throw (ex-info (format "%s : Path %s does not exist." key file) {}))))

(defn verify-directory-non-empty
  "Check if `dir` contains at least one file. Also checks that `dir` exists."
  [key dir]
  (verify-file-exists key dir)
  (when-not (seq (.list dir))
    (throw (ex-info (format "%s : Directory %s is empty." key dir) {}))))
