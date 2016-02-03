(defproject com.grammarly/omniconf "0.1.1"
  :description "Fancy configuration library for your Clojure programs"
  :url "https://github.com/grammarly/omniconf"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [cloverage "1.0.6"]]
                   :plugins [[lein-shell "0.5.0"]]
                   :aliases {"coverage" ["do" ["run" "-m" "omniconf.t-coverage" "--coveralls"]]}}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}})
