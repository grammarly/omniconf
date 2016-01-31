(ns example-boot.core
  (:require [omniconf.core :as cfg])
  (:gen-class))

(defn init-config [cli-args quit-on-error]
 (cfg/define
   {:help {:description "prints this help message"
           :help-name "my-script"
           :help-description "description of the whole script"}
    :boolean-option {:description "can be either t or nil, doesn't need a value in CLI options"}
    :string-option {:parser identity
                    :description "this option's value is taken as is"}
    :integer-option {:parser cfg/parse-number
                     :description "parsed as integer"}
    :lisp-option {:parser cfg/parse-edn
                  :description "read as lisp structure"
                  :default '(1 2)}
    :file-option {:parser cfg/parse-filename
                  :description "read as filename"}
    :directory-option {:parser cfg/parse-directory
                       :description "read as directory name"}
    :option-with-default {:parser cfg/parse-number
                          :default 1024
                          :description "has a default value"}
    :required-option {:parser identity
                      :required true
                      :description "must have a value before call to `verify`, otherwise fails"}
    :conditional-option {:parser identity
                         :required-if (fn [] (= (cfg/get :option-with-default) 2048))
                         :description "must have a value if a condition applies"}
    :option-from-set {:parser keyword
                      :one-of #{:foo :bar :baz}
                      :description "value must be a member of the provided list"}
    :existing-file-option {:parser cfg/parse-filename
                           :verifier cfg/verify-file-exists
                           :description "file should exist"}
    :nonempty-dir-option {:parser cfg/parse-directory
                          :verifier cfg/verify-directory-non-empty
                          :description "directory must have files"}
    :delayed-option {:parser cfg/parse-number
                     :delayed-transform (fn [v] (+ v 5))
                     :description "has a custom transform that is called the first time the option is read"}
    :conf-file {:parser cfg/parse-filename
                :verifier cfg/verify-file-exists
                :description "it is idiomatic to provide a configuration file just as another option"}
    :nested-option {:description "has child options"
                    :default {:first "alpha"}
                    :nested {:first {:description "nested option one"
                                     :parser identity}
                             :second {:description "nested option two"
                                      :parser cfg/parse-number
                                      :default 70}
                             :more {:nested {:one {:parser identity
                                                   :default "one"}
                                             :two {:parser identity}}}}}})

  (cfg/populate-from-opts cli-args quit-on-error)
  (when-let [conf-file (cfg/get :conf-file)]
    (cfg/populate-from-file conf-file))
  (cfg/populate-from-env quit-on-error)

  (cfg/verify :quit-on-error quit-on-error))

(defn run-application []
  (println "Now actually starting the app...")
  (println "Option-from-set is" (cfg/get :option-from-set)))

(defn verify
  "Only sets up config and verifies it, without running application code."
  [& args]
  (init-config args true))

(defn -main [& args]
  (init-config args true)
  (run-application))
