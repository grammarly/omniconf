(ns omniconf.ssm-test
  (:require [clojure.test :refer :all]
            [omniconf.core :as cfg]
            [omniconf.ssm :as sut])
  (:import com.amazonaws.services.simplesystemsmanagement.model.Parameter
           java.util.Date))

(def ^:dynamic *ssm-mock*
  (let [modified #inst "2020-01-01T00:00:00.000-00:00"]
    {"/prod/myapp/db/user"         ["DBUSER" modified]
     "/prod/myapp/db/password"     ["123456" modified]
     "/prod/myapp/db/port"         ["3306" modified]
     "/prod/myapp/application-url" ["example.com" modified]
     "/myteam/github/oauth-token"  ["aaabbb" modified]}))

(defn get-parameter-mock [ssm-key-name]
  (when-let [[val modified] (get *ssm-mock* ssm-key-name)]
    (doto (Parameter.)
      (.setName ssm-key-name)
      (.setValue val)
      (.setLastModifiedDate modified))))

(defn get-parameters-mock [path]
  (doall
   (for [^String k (keys *ssm-mock*)
         :when (.startsWith k path)]
     (get-parameter-mock k))))

(defn populate-from-ssm-mock [path]
  (with-redefs-fn {#'sut/get-parameter get-parameter-mock
                   #'sut/get-parameters get-parameters-mock}
    #(sut/populate-from-ssm path)))

(defn redefine []
  (cfg/define
    {:db {:nested {:user {:type :string}
                   :password {:type :string
                              :secret true}
                   :port {:type :number}}}
     :github-token {:type :string
                    :secret true
                    :ssm-name "/myteam/github/oauth-token"}
     :url {:type :string
           :ssm-name "./application-url"}}))

(deftest ssm-test
  (redefine)
  (populate-from-ssm-mock "/prod/myapp/")

  (is (= "DBUSER" (cfg/get :db :user)))
  (is (= "123456" (cfg/get :db :password)))
  (is (= 3306 (cfg/get :db :port)))
  (is (= "aaabbb" (cfg/get :github-token)))
  (is (= "example.com" (cfg/get :url)))

  (testing "value can be updated"
    (binding [*ssm-mock* (assoc *ssm-mock* "/prod/myapp/db/port" ["8080" (Date.)])]
      (populate-from-ssm-mock "/prod/myapp/"))
    (is (= 8080 (cfg/get :db :port))))

  (testing "value can be unset"
    (binding [*ssm-mock* (assoc *ssm-mock* "/myteam/github/oauth-token" ["__SSM__UNSET__" (Date.)])]
      (populate-from-ssm-mock "/prod/myapp/"))
    (is (nil? (cfg/get :github-token)))))
