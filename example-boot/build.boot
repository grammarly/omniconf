(set-env!
 :dependencies '[[com.grammarly/omniconf "0.2.2"]]
 :source-paths #{"src/"})

;; Because Boot doesn't allow to directly pass arguments to tasks omitting its
;; own CLI framework, we have to add this hack here.
;; https://github.com/boot-clj/boot/issues/374
(alter-var-root
 #'boot.core/construct-tasks
 (constantly
  (fn [& argv]
    (loop [ret [] [op-str & args] argv]
      (if-not op-str
        (apply comp (filter fn? ret))
        (let [op (-> op-str symbol resolve)]
          (when-not (and op (:boot.core/task (meta op)))
            (throw (IllegalArgumentException. (format "No such task (%s)" op-str))))
          (if (:raw-args (meta op))
            (recur (conj ret (apply (var-get op) (rest argv))) [])

            (let [spec   (:argspec (meta op))
                  parsed (boot.from.clojure.tools.cli/parse-opts args spec :in-order true)]
              (when (seq (:errors parsed))
                (throw (IllegalArgumentException. (clojure.string/join "\n" (:errors parsed)))))
              (let [[opts argv] (#'boot.cli/separate-cli-opts args spec)]
                (recur (conj ret (apply (var-get op) opts)) argv))))))))))

(defn ^:boot.core/task ^:raw-args run
  "Run the project."
  [& args]
  (require 'example-boot.core)
  (apply (resolve 'example-boot.core/-main) args))

(defn ^:boot.core/task ^:raw-args verify
  "Verifies that the project is properly configured."
  [& args]
  (require 'example-boot.core)
  (apply (resolve 'example-boot.core/verify) args))
