# Omniconf [![CircleCI](https://img.shields.io/circleci/build/github/grammarly/omniconf/master.svg)](https://dl.circleci.com/status-badge/redirect/gh/grammarly/omniconf/tree/master) [![](https://img.shields.io/clojars/dt/com.grammarly/omniconf?color=teal)](https://clojars.org/com.grammarly/omniconf) [![](https://img.shields.io/badge/-changelog-blue.svg)](CHANGELOG.md)

Command-line arguments. Environment variables. Configuration files. Java
properties. Almost every program requires some configuration which is spread
across multiple sources. Keeping track of values in those sources, mapping ones
to others, making sure they are present and correct, passing them around is a
laborious and thankless task.

There are several popular configuration libraries for Clojure. What makes
Omniconf different is that it separates the configuration's "blueprint" (which
lives in a single place within the code) from the "values" (which can come from
many places). You may think of Omniconf as
"[tools.cli](https://github.com/clojure/tools.cli) but not just for command-line
arguments". By declaring upfront what you expect the configuration to be, you
gain the ability to to validate the received values early and display helpful
messages if the application is misconfigured.

In terms of configuration sources, Omniconf supports:

- Environment variables
- CLI arguments
- Java properties
- [EDN files](#providing-configuration-as-files)
- [AWS SSM (+dynamic reconfiguration)](#fetching-configuration-from-aws-systems-manager-ssm)

## Rationale

Omniconf is developed with the following principles in mind:

1. **Explicit over implicit.** Most configuration libraries let you grab a
   configuration value (e.g. from ENV) at any point in time, from any place in
   the code. This makes it impossible to list all configuration options that the
   program uses without reading the entire source. Omniconf requires you to
   declare all possible configuration options upfront, at the start of the
   program.
2. **All configuration sources must be unified.** It doesn't matter where the
   values come from: they are uniformly initialized, verified, and accessed as
   regular Clojure data.
3. **Early validation.** You don't want to see NumberFormatException stacktraces
   in the middle of your program run because of a typo in the environment
   variable. The whole configuration should be verified early before the rest of
   the code is executed. If there are any problems with it, the user should be
   presented a helpful diagnostic message.

## Usage

Add Omniconf to your dependencies:

[![](https://clojars.org/com.grammarly/omniconf/latest-version.svg)](https://clojars.org/com.grammarly/omniconf)

1. You start by defining the blueprint of your configuration. `cfg/define` takes
   a map of options to their parameters. The following small example gives a
   glimpse of the syntax:

   ```clj
   (require '[omniconf.core :as cfg])
   (cfg/define
     {:hostname {:description "where service is deployed"
                 :type :string
                 :required true}
      :port     {:description "HTTP port"
                 :type :number
                 :default 8080}})
   ```

   The full list of supported parameters is described
   [here](#configuration-scheme-syntax).

2. Populate the configuration from available sources:

   ```clj
   (cfg/populate-from-cmd args) ;; args is a command-line arguments list
   (when-let [conf (cfg/get :conf)]
     (cfg/populate-from-file conf))
   (cfg/populate-from-properties)
   (cfg/populate-from-env)
   ```

   The order in which to tap the sources is up to you. Maybe, you want to make
   environment variables overwrite command-line args, or vice versa, or give the
   highest priority to the config file. In the above example we get the path to
   the configuration file as `--conf` CMD argument. For more information, see
   [this](#providing-configuration-as-files).

3. Call `verify`. It marks the boundary in your system, after which the whole
   configuration is guaranteed to be complete and correct.

   ```clj
   (cfg/verify)
   ```

   If there is something wrong with the configuration, `verify` will throw a
   proper exception. If called not in the REPL environment, the exception will
   be stripped of its stacktrace, so that you only see a human-readable message.

   If everything is alright, `verify` will pretty-print the whole configuration
   map into the standard output. This allows you to manually make sure that the
   values are correct. `:silent true` can be passed to `verify` to prevent it
   from printing the map.

4. Use `get` to extract arbitrary value from the configuration.

   ```clj
   (cfg/get :hostname)
   ```

   For nested values you can pass the path to the value, either as a vector, or
   like varargs:

   ```clj
   (cfg/get :database :ip)
   (cfg/get [:database :ip])
   ```

   `set` allows you to change a value at runtime. It is not recommended to be
   used in production code, but may be convenient during development:

   ```clj
   (cfg/set :database :port 3306)
   (cfg/set [:database :port] 3306)
   ```

## Example

Sample project that uses Omniconf with tools.deps: [example](./example). There
is not much difference if you build and then run your application directly via a
main class, you will still pass the CLI arguments from `-main` to Omniconf; and
populating other sources won't require any changes.

## Configuration scheme syntax

Configuration scheme is a map of option names to maps of their parameters.
Option name is a keyword that denotes how the option is retrieved inside the
program, and how it maps to configuration sources. Naming rules are the
following:

For command-line arguments:

    :some-option    =>    --some-option

For environment variables:

    :some-option    =>    SOME_OPTION

For Java properties:

    :some-option    =>    some-option   (java -Dsome-option=... if set from command line)

Each option can have the following parameters:

- `:description` — string that describes this option. This description will be
  used to generate the help message for the program.

- `:type` — the following types are supported: `:string`, `:keyword`, `:number`,
  `:boolean`, `:edn`, `:file`, `:directory`. Setting a type automatically
  defines how to parse a value for this option from a string, and also verifies
  that the resulting value has the correct Clojure type.

  `:boolean` options get a special treatment. When setting them from the command
  line, one can omit the value completely.

  ```clj
  (cfg/define {:foo {:type :boolean}
               :bar {:type :boolean}})
  ```

  ```shell
  $ my-app --foo --bar    # Confmap is {:foo true, :baz true}
  ```

  String parser for booleans treats strings "0" and "false" as `false`, anything
  else as `true`.

- `:parser` — a single-arg function that converts a string value (given in
  command-line option or environment variable) into a Clojure value. This
  parameter can be used instead of `:type` if you need a custom option type.

- `:default` — the option will be initialized with this value if no sources
  provide an explicit value for it. The default value must be given as a Clojure
  datatype, not as a string yet to be parsed.

  The value for `:default` can be a nullary function used to generate the actual
  default value. This function will be invoked during the verification phase or
  on first direct access to the value, whichever happens first; thus, default
  functions can access other config values provided by the user. Example:

  ```clj
  (cfg/define {:host {:type :string}
               :port {:type :number}
               :connstring {:type :string
                            :default #(str (cfg/get :host) ":" (cfg/get :port))}})
  (cfg/populate-from-map {:host "localhost", :port 8888})
  (cfg/get :connstring) ;; => "localhost:8888"
  ```

  Even if a config option has a default function, its value can be explicitly
  set from any configuration source to a normal value, and in that case the
  default function won't be invoked.

  Make sure that you don't try to `cfg/get` an option with a default function
  before the values that function depends on are populated.

- `:required` — if true, the value for this option must be provided, otherwise
  `verify` will fail. The value of this parameter can also be a nullary
  function: if the function returns true then the option value must be provided.
  It is convenient if the necessity of an option depends on the values of some
  other options. Example:

  ```clj
  (cfg/define {:storage   {:one-of [:file :s3]}
               :s3-bucket {:required #(= (cfg/get :storage) :s3)}})
  ```

- `:one-of` — a sequence of values that an option is allowed to take. If the
  value isn't present in the `:one-of` list, `verify` will fail. `:one-of`
  automatically implies `:required true` unless you add `nil` as a permitted
  value.

- `:verifier` — a function of `[option-name value]` that should throw an
  exception if the value is not correct. Verifier is only executed if the value
  is not nil, so it doesn't imply `:required true`. Predefined verifiers:
  + `cfg/verify-file-exists`
  + `cfg/verify-directory-non-empty` — checks if the value is a directory, and
    if it is non-empty.

- `:delayed-transform` — a function to transform the value of the option into
  some other value when it is accessed for the first time. Transform will be
  applied only once, and after that the option will store the transformed value.
  You could mimic this feature by using a custom parser that wraps the
  transformation in a `delay`, the only difference that you would also have to
  dereference it manually every time.

- `:nested` — a map that has the same structure as the top-level configuration
  scheme. Nested options have the same rights as top-level ones: they can have
  parsers, verifiers, defaults, etc. Example:

  ```clj
  (cfg/define
    {:statsd {:nested {:host {:type :string
                              :required true
                              :description "IP address of the StatsD server"}
                       :port {:type :number
                              :default 8125}}}})
  ```

  CLI and ENV arguments have special transformation rules for nested options —
  dot as a separator for CLI arguments and Java properties, and double
  underscore for ENV.

  ```
  [:statsd :host]    =>    --statsd.host   (cmdline args)
  [:statsd :host]    =>    -Dstatsd.host   (properties)
  [:statsd :host]    =>    STATSD__HOST    (env variables)
  ```

  In the code, you can use `cfg/get` to fetch a terminal value or a submap map
  at any level:

  ```clj
  (cfg/get :statsd :port) ;=> 8125
  (cfg/get :statsd) ;=> {:host "127.0.0.1", :port 8125}
  ```

- `:secret` — if true, the value of this option won't be printed out by
  `cfg/verify`. You will see `<SECRET>` instead. Useful for passwords, API keys,
  and such.

## Providing configuration as files

Omniconf can use EDN files as a configuration source. A file must contain a map
of options to their values, which will be merged into the config when
`populate-from-file` is called. The values should already have the format the
option requires (number, keyword); but you can also use strings so that parser
will be called on them.

You can hardcode the name of the file where to look for configuration (e.g.
`config.edn` in the current directory). It is somewhat trickier to tell the name
of the file dynamically. One of the solutions is to expect the configuration
file to be provided in one of the command-line arguments. So you have to
`populate-from-cmd` first, and then to populate from config file if it has been
provided. However, this way the configuration file will have the priority over
CLI arguments which is not always desirable. As a workaround, you can call
`populate-from-cmd` again, but only if your CLI args are idempotent (i.e. they
don't contain `^:concat`, see below).

Optionally, `data-readers` may be provided when reading from a file which
contain library specific reader macros. For example:

```clj
(cfg/populate-from-cmd args) ;; args is a command-line arguments list
(when-let [conf (cfg/get :conf)]
  (binding [cfg/*data-readers* {'ig/ref ig/ref 'ig/refset ig/refset}] ; e.g. reader macros used by integrant
    (cfg/populate-from-file conf)))
```

## Fetching configuration from AWS Systems Manager (SSM)

Omniconf supports [Amazon SSM](https://aws.amazon.com/systems-manager/),
particularly its [Parameter
Store](https://aws.amazon.com/systems-manager/features/), as a configuration
source. SSM works well as a storage for secrets — passwords, tokens, and other
sensitive things that you don't want to check into the source control.

To use SSM backend, you'll need to add an extra dependency:

[![](https://clojars.org/com.grammarly/omniconf.ssm/latest-version.svg)](https://clojars.org/com.grammarly/omniconf.ssm)

The function `omniconf.core/populate-from-ssm` will be available now. It takes
`path` as an argument which will be treated as root path to nested SSM
parameters. For example:

```clj
(cfg/define
  {:db {:nested {:password {:type :string
                            :secret true}}}})

(cfg/populate-from-ssm "/prod/myapp/")
```

This will fetch `/prod/myapp/db/password` parameter from SSM and save it as
`[:db :password]` in Omniconf.

You can also specify explicit mapping between SSM and Omniconf like this:

```clj
(cfg/define
  {:db {:nested {:password {:type :string
                            :secret true}}}
   :github-token {:type :string
                  :secret true
                  :ssm-name "/myteam/github/oauth-token"}})

(cfg/populate-from-ssm "/prod/myapp/")
```

Parameters with an absolute `:ssm-name` parameter will ignore the `path`
argument and will fetch the value directly by name. In case you still want to
use `path` for some keys but the layout in SSM differs from one in Omniconf, you
can use `./` as a prefix to signify that it is relative to the path:

```clj
(cfg/define
  {:db {:nested {:password {:type :string
                            :secret true
                            :ssm-name "./db-pass"}}}})

(cfg/populate-from-ssm "/prod/myapp/")
```

This will set `[:db :password]` parameter from `/prod/myapp/db-pass`.

### Dynamic reconfiguration from SSM

Unlike environment variables and command-line arguments, SSM Parameter Store
values can change independently as your program is running. You may want to
react to those changes without restarting the program. There are plenty of
usecases for this, like switching downstream URLs on the fly or gradually
changing the rate of requests to an experimental server you are testing.

To tap into this functionality, use `populate-from-ssm-continually` instead of
`populate-from-ssm`. It accepts the same `path` argument, and an extra one —
interval in seconds between polling SSM. Polling is used because SSM doesn't
expose an event-based API for this; but the overhead won't be significant if you
set the interval to 5-10 seconds. Also, Omniconf would report resetting only the
values that actually have changed.

```clj
;; Poll values under /prod/myapp/ prefix (and all absolute :ssm-name values too) every 10 seconds.
(cfg/populate-from-ssm-continually "/prod/myapp/" 10)
```

Note that the verification step is not re-run after fetching updated values from
SSM, so it is possible to break `:verifier` invariants with this.

### Unsetting a parameter through SSM

It is possible to "unset" an already established Omniconf parameter by setting
the string value of the corresponding SSM key to `__SSM__UNSET__`. This works
regardless of the type of the parameter. If Omniconf encounters an SSM parameter
with this special string value, it will dissoc the respective key from the
current config map.

NB: this approach doesn't check if the value being unset originally came from
SSM. It also does not "undo" an SSM overwrite, but literally removes the value.
So, for example, if the default value of the parameter was `1`, then you changed
it using dynamic SSM to `2`, and then change the SSM parameter to
`__SSM__UNSET__`, the value within Omniconf won't go back to `1`, instead the
key-value pair will be completely removed from the map.

## Tips, tricks, and FAQ

### What are the drawbacks?

Omniconf is more complex and intertwined than
[Environ](https://github.com/weavejester/environ) or even
[Aero](https://github.com/juxt/aero). It involves more ceremony (but also adds
more robustness and centralizes the source of truth).

Omniconf configuration map is a global mutable singleton. It adds a bit of
convenience that you don't have to drag the config map around. However, there
might be usecases where this approach does not fit.

Omniconf is an application-level tool. You most likely don't want to make
your library depend on it, forcing the library users to configure through
Omniconf too.

### CLI help command

`:help` option gets a special treatment in Omniconf. It can have `:help-name`
and `:help-description` parameters that will be used when printing the help
message. If `populate-from-cmd` encounters `--help` on the arguments list, it
prints the help message and quits.

### Special operations for EDN options

Sometimes, you don't want to completely overwrite an EDN value, but append to
it. For this case two special operations, — `^:concat` and `^:merge` — can be
attached to a map or a list when setting them from any source. Example:

```clj
(cfg/define {:emails {:type :edn
                      :default ["admin1@corp.org" "admin2@corp.org"]}
             :roles  {:type :edn
                      :default {"admin1@corp.org" :admin
                                "admin2@corp.org" :admin}}})
```

```shell
$ my-app --emails '^:concat ["user1@corp.org"]' --roles '^:merge {"user1@corp.org" :user}'
```

### Custom logging for Omniconf

By default, Omniconf prints errors and final configuration map to standard
output. If you want it to use a special logging solution, call
`cfg/set-logging-fn` and provide a vararg function for Omniconf to use it
instead of `println`. For example:

```clj
(require '[taoensso.timbre :as log])
(cfg/set-logging-fn (fn [& args] (log/info (str/join " " args))))
```

## License

© Copyright 2016-2023 Grammarly, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
