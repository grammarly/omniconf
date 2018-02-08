(set-env!
 :dependencies '[[com.grammarly/omniconf "0.2.7"]]
 :source-paths #{"src/"})

(deftask run
  [a args ARGS str "CLI args to pass to Omniconf"]
  (require 'example-boot.core)
  (apply (resolve 'example-boot.core/-main) (.split (or args "") " ")))

(deftask verify
  "Verifies that the project is properly configured."
  [a args ARGS str "CLI args to pass to Omniconf"]
  (require 'example-boot.core)
  (apply (resolve 'example-boot.core/verify) (.split (or args "") " ")))
