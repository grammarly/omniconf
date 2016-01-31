# example-boot

Navigate to this directory and execute:

``` OPTION_FROM_SET=foo boot run --required-option ```

Experiment with configuration to change it, break it etc.

You can also run only the verification step:

``` boot verify --required-option --option-from-set=bar ```

Notice that `build.boot` contains a hack as a temporary workaround for
https://github.com/boot-clj/boot/issues/374.
