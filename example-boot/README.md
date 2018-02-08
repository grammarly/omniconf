# example-boot

_Boot doesn't allow to pass arguments to a task bypassing its own CLI framework.
Because of this, the example uses a single `-a` option that takes a string with
options for Omniconf._

Navigate to this directory and execute:

``` OPTION_FROM_SET=foo boot run -a '--required-option bar' ```

Experiment with configuration to change it, break it etc.

You can also run only the verification step:

``` boot verify -a '--required-option foo --option-from-set bar --boolean-option' ```

Run help to see all options:

``` boot run -a '--help' ```
