(set-env!
 :dependencies '[[com.grammarly/omniconf "0.3.2"]]
 :source-paths #{"src/"})

(deftask run
  [a args ARGS str "CLI args to pass to Omniconf"]
  (require 'example-boot.core)
  (apply (resolve 'example-boot.core/-main) (.split (or args "") " ")))
