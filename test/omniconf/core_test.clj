(ns omniconf.core-test
  (:require [omniconf.core :as cfg]
            [clojure.java.io :as io]
            [clojure.test :refer :all]))

(cfg/enable-functions-as-defaults)

(cfg/define
  {:help {:description "prints this help message"
          :help-name "my-script"
          :help-description "description of the whole script"}
   :boolean-option {:description "can be either true or false"
                    :type :boolean}
   :string-option {:type :string
                   :description "this option's value is taken as is"}
   :integer-option {:type :number
                    :description "parsed as integer"}
   :edn-option {:type :edn
                :description "read as EDN structure"
                :default '(1 2)}
   :file-option {:type :file
                 :description "read as filename"}
   :directory-option {:type :directory
                      :description "read as directory name"}
   :option-with-default {:parser cfg/parse-number
                         :default 1024
                         :description "has a default value"}
   :required-option {:type :string
                     :required true
                     :description "must have a value before call to `verify`, otherwise fails"}
   :conditional-option {:parser identity
                        :required (fn [] (= (cfg/get :option-with-default) 2048))
                        :description "must have a value if a condition applies"}
   :option-from-set {:type :keyword
                     :one-of #{:foo :bar :baz nil}
                     :description "value must be a member of the provided list"}
   :option-from-set-with-nil {:type :keyword
                              :one-of #{:foo :bar nil}
                              :description "value must be a member of the provided list, but not required"}
   :existing-file-option {:parser cfg/parse-filename
                          :verifier cfg/verify-file-exists
                          :description "file should exist"}
   :nonempty-dir-option {:parser cfg/parse-filename
                         :verifier cfg/verify-directory-non-empty
                         :description "directory must have files"}
   :delayed-option {:type :number
                    :delayed-transform (fn [v] (+ v 5))
                    :description "has a custom transform that is called the first time the option is read"}
   :renamed-option {:type :boolean
                    :env-name "MY_OPTION"
                    :opt-name "custom-option"
                    :prop-name "property-custom-option"
                    :description "has custom names for different sources"}
   :secret-option {:type :string
                   :secret true}
   :conf-file {:type :file
               :verifier cfg/verify-file-exists
               :description "it is idiomatic to provide a configuration file just as another option"}
   :nested-option {:description "has child options"
                   :default {:first "alpha"}
                   :nested {:first {:description "nested option one"
                                    :type :string}
                            :second {:description "nested option two"
                                     :type :number
                                     :default 70}
                            :file {:description "nested file option"
                                   :type :file}
                            :more {:nested {:one {:type :string
                                                  :default "one"}
                                            :two {:type :string}}}}}
   :nested-default-fn {:nested {:width {:type :number
                                        :default 10}
                                :height {:type :number
                                         :default 20}
                                :area {:type :number
                                       :default #(* (cfg/get :nested-default-fn :width)
                                                    (cfg/get :nested-default-fn :height))}}}
   :delayed-nested {:nested {:delayed {:default "foo"
                                       :delayed-transform #(str % "bar")}}}})

(defn check-basic-options []
  (is (nil? (cfg/verify :silent true)))
  (is (= true (cfg/get :boolean-option)))
  (is (= "bar" (cfg/get :string-option)))
  (is (= 42 (cfg/get :integer-option)))
  (is (= '(1 2 3) (cfg/get :edn-option)))
  (is (= (java.io.File. "CHANGELOG.md") (cfg/get :file-option)))
  (is (= (java.io.File. "test/") (cfg/get :directory-option)))
  (is (= 2048 (cfg/get :option-with-default)))
  (is (= :baz (cfg/get :option-from-set)))
  (is (= nil (cfg/get :option-from-set-with-nil)))
  (is (= 15 (cfg/get :delayed-option)))
  (is (= true (cfg/get :renamed-option)))
  (is (= {:first "alpha", :more {:two "two"}, :second 70} (cfg/get :nested-option)))
  (is (= "alpha" (cfg/get :nested-option :first)))
  (is (= 70 (cfg/get :nested-option :second)))
  (is (= {:two "two"} (cfg/get :nested-option :more)))
  (is (= "two" (cfg/get :nested-option :more :two))))

(deftest basic-options-cmd
  (reset! @#'cfg/config-values (sorted-map))
  (#'cfg/fill-default-values)
  (cfg/populate-from-cmd
   ["--required-option" "foo" "--string-option" "bar"
    "--integer-option" "42" "--edn-option" "^:concat (3)" "--file-option" "CHANGELOG.md"
    "--directory-option" "test" "--option-with-default" "2048"
    "--conditional-option" "dummy" "--option-from-set" "baz"
    "--delayed-option" "10" "--custom-option" "--nested-option.more" "{}"
    "--nested-option.more.two" "two" "--boolean-option"])
  (check-basic-options))

(deftest basic-options-prop
  (reset! @#'cfg/config-values (sorted-map))
  (#'cfg/fill-default-values)

  (System/setProperty "required-option" "foo")
  (System/setProperty "string-option" "bar")
  (System/setProperty "integer-option" "42")
  (System/setProperty "edn-option" "^:concat (3)")
  (System/setProperty "file-option" "CHANGELOG.md")
  (System/setProperty "directory-option" "test")
  (System/setProperty "option-with-default" "2048")
  (System/setProperty "conditional-option" "dummy")
  (System/setProperty "option-from-set" "baz")
  (System/setProperty "delayed-option" "10")
  (System/setProperty "property-custom-option" "true")
  (System/setProperty "nested-option.more" "{}")
  (System/setProperty "nested-option.more.two" "two")
  (System/setProperty "boolean-option" "true")

  (cfg/populate-from-properties)
  (check-basic-options))

(deftest basic-options-map
  (reset! @#'cfg/config-values (sorted-map))
  (#'cfg/fill-default-values)
  (cfg/populate-from-map {:nested-option {:more {}}})
  ;; Hack because setting from map doesn't allow overriding whole nested maps.
  (cfg/set :nested-option :more {})
  (cfg/populate-from-map {:required-option "foo"
                          :string-option "bar"
                          :integer-option 42
                          :edn-option "^:concat (3)"
                          :file-option "CHANGELOG.md"
                          :directory-option "test"
                          :option-with-default 2048
                          :conditional-option "dummy"
                          :option-from-set "baz"
                          :delayed-option 10
                          :renamed-option true
                          :nested-option {:more {:two "two"}}
                          :boolean-option true})
  (check-basic-options))

(deftest basic-options-file
  (reset! @#'cfg/config-values (sorted-map))
  (#'cfg/fill-default-values)
  ;; Hack because setting from file doesn't allow overriding whole nested maps.
  (cfg/set :nested-option :more {})
  (cfg/populate-from-file "test/omniconf/test-config.edn")
  (check-basic-options))

(deftest extended-functionality
  (reset! @#'cfg/config-values (sorted-map))
  (#'cfg/fill-default-values)
  (cfg/populate-from-cmd
   ["--required-option" "foo" "--string-option" "bar"
    "--integer-option" "42" "--file-option" "CHANGELOG.md"
    "--directory-option" "test" "--option-with-default" "2048"
    "--conditional-option" "dummy" "--option-from-set" "baz"
    "--delayed-option" "10" "--custom-option" "--nested-option.more" "{}"
    "--nested-option.more.two" "two" "--boolean-option"])

  (testing "fine-so-far"
    (is (nil? (cfg/verify :silent true))))

  (testing "required"
    (cfg/set :required-option nil)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :required-option "foo"))

  (testing "one-of"
    (cfg/set :option-from-set :bar)
    (is (nil? (cfg/verify :silent true)))
    (cfg/set :option-from-set :notbar)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :option-from-set :bar))

  (testing "conditional-required"
    (cfg/set :option-with-default 2048)
    (cfg/set :conditional-option "foo")
    (is (nil? (cfg/verify :silent true)))
    (cfg/set :conditional-option nil)
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :option-with-default 1024)
    (is (nil? (cfg/verify :silent true))))

  (testing "delayed transform works for nested values"
    (is (= "foobar" (cfg/get :delayed-nested :delayed))))

  (testing "secret"
    (cfg/set :secret-option "very-sensitive-data")
    (is (= -1 (.indexOf (with-out-str (cfg/verify)) "very-sensitive-data"))))

  (testing "verify-file-exists"
    (cfg/set :existing-file-option (io/file "nada.clj"))
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :existing-file-option (io/file "CHANGELOG.md")))

  (testing "verify-nonempty-dir"
    (.mkdirs (io/file "target" "_empty_"))
    (cfg/set :nonempty-dir-option (io/file "target" "_empty_"))
    (is (thrown? Exception (cfg/verify)))
    (cfg/set :nonempty-dir-option (io/file "test")))

  (testing "populate-from-opts"
    (is (thrown? Exception (cfg/populate-from-cmd ["--string-option" "bar" "baz"]))))

  (testing "print-cli-help"
    (is (not= "" (with-out-str (#'cfg/print-cli-help)))))

  (testing "with-options"
    (cfg/with-options [option-with-default]
      (is (= 1024 option-with-default))))

  (testing "populate-from-env"
    (cfg/populate-from-env)
    (cfg/verify :silent true))

  (testing "populate-from-file"
    (cfg/populate-from-file "test/omniconf/test-config.edn")
    (cfg/verify :silent true))

  (testing "parsing sanity-check"
    (is (thrown? Exception (cfg/populate-from-cmd ["--nested-option" "foo"])))
    (is (thrown? Exception (cfg/populate-from-cmd ["--integer-option" "garbage"]))))

  (testing "default functions"
    (is (= {:area 200, :height 20, :width 10} (cfg/get :nested-default-fn)))

    (reset! @#'cfg/config-values (sorted-map))
    (#'cfg/fill-default-values)
    (cfg/populate-from-file "test/omniconf/test-config.edn")
    (cfg/populate-from-map {:nested-default-fn {:width 100 :height 200}})
    (cfg/verify)
    (is (= {:area 20000, :height 200, :width 100} (cfg/get :nested-default-fn)))

    (reset! @#'cfg/config-values (sorted-map))
    (#'cfg/fill-default-values)
    (cfg/populate-from-file "test/omniconf/test-config.edn")
    (cfg/populate-from-map {:nested-default-fn {:width 100 :height 200 :area 42}})
    (cfg/verify)
    (is (= {:area 42, :height 200, :width 100} (cfg/get :nested-default-fn))))
  )
