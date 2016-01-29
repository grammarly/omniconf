(ns omniconf.t-core
  (:require [omniconf.core :as cfg]
            [clojure.java.io :as io]
            [clojure.test :refer :all]))

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
   :renamed-option {:env-name "MY_OPTION"
                    :opt-name "custom-option"
                    :description "has custom names for different sources"}
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

(deftest basic-options
  (cfg/populate-from-opts
   ["--required-option" "foo" "--boolean-option" "--string-option" "bar"
    "--integer-option" "42" "--lisp-option" "^:concat (3)" "--file-option" "all.lisp"
    "--directory-option" "test" "--option-with-default" "2048"
    "--conditional-option" "dummy" "--option-from-set" "baz"
    "--delayed-option" "10" "--custom-option" "--nested-option.more" "{}"
    "--nested-option.more.two" "two"])

  (is (nil? (cfg/verify :silent true)))
  (is (= true (cfg/get :boolean-option)))
  (is (= "bar" (cfg/get :string-option)))
  (is (= 42 (cfg/get :integer-option)))
  (is (= '(1 2 3) (cfg/get :lisp-option)))
  (is (= (java.io.File. "all.lisp") (cfg/get :file-option)))
  (is (= (java.io.File. "test/") (cfg/get :directory-option)))
  (is (= 2048 (cfg/get :option-with-default)))
  (is (= :baz (cfg/get :option-from-set)))
  (is (= 15 (cfg/get :delayed-option)))
  (is (= true (cfg/get :renamed-option)))
  (is (= {:first "alpha", :more {:two "two"}, :second 70} (cfg/get :nested-option)))
  (is (= "alpha" (cfg/get :nested-option :first)))
  (is (= 70 (cfg/get :nested-option :second)))
  (is (= {:two "two"} (cfg/get :nested-option :more)))
  (is (= "two" (cfg/get :nested-option :more :two))))

(deftest extended-functionality
  (cfg/set :required-option true)
  (cfg/set :option-from-set :bar)
  (cfg/set :file-option (io/file "test/errors.lisp"))
  (cfg/set :dir-option (io/file "test/"))

  (deftest fine-so-far
    (is (nil? (cfg/verify :silent true))))

  (deftest required
    (cfg/set :required-option nil)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :required-option true))

  (deftest one-of
    (cfg/set :option-from-set :bar)
    (is (nil? (cfg/verify :silent true)))
    (cfg/set :option-from-set :notbar)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :option-from-set :bar))

  (deftest conditional-required
    (cfg/set :option-with-default 2048)
    (cfg/set :conditional-option "foo")
    (is (nil? (cfg/verify :silent true)))
    (cfg/set :conditional-option nil)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :option-with-default 1024)
    (is (nil? (cfg/verify :silent true))))

  (deftest verify-file-exists
    (cfg/set :existing-file-option (io/file "nada.clj"))
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :existing-file-option (io/file "build.boot")))

  (deftest verify-nonempty-dir
    (.mkdirs (io/file "target" "_empty_"))
    (cfg/set :nonempty-dir-option (io/file "target" "_empty_"))
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :nonempty-dir-option (io/file "test"))))
