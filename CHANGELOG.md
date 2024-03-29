# Changelog

### 0.5.0-SNAPSHOT (unreleased)

- **BREAKING:** arities that had `quit-on-error` argument are now removed.
- **BREAKING:** if the value for `:default` field is a function, it is now by
  default interpreted as a default initializer function. The function
  `(cfg/enable-functions-as-defaults)` is preserved for backwards compatibility
  but does nothing now and will be removed in the future version.
- Add ability to unset values from SSM.
- [#23](https://github.com/grammarly/omniconf/issues/23) Fix bug where
  `populate-from-ssm` would assume node role instead of pod role when running in
  EKS.

### 0.4.3 (2021-03-29)

- Prevent exceptions raised in `populate-from-ssm` from breaking the periodic
  scheduler.

### 0.4.2 (2020-08-07)

- `:default` field for an option can now be a nullary function that is invoked
  during the verification phase to generate the actual value based on other
  config values. For now, this feature is opt-in to preserve the backwards
  compatibility (for hypothetical cases where someone would use Omniconf
  defaults to store functions). You need to call
  `(cfg/enable-functions-as-defaults)` for this feature to work, but this will
  change in the next minor version.
- [#16](https://github.com/grammarly/omniconf/issues/16) Add the ability to
  specify data readers when loading configuration from a file.

### 0.4.1 (2019-12-06)

- [#14](https://github.com/grammarly/omniconf/issues/14) Fix bug in SSM poller
  where values configured by absolute path would be erased.
- [#15](https://github.com/grammarly/omniconf/issues/15) Force newer Jackson
  dependencies to protect from CVE-2018-14719.

### 0.4.0 (2019-06-04)

- [#13](https://github.com/grammarly/omniconf/issues/13) Fetch all SSM
  parameters for a given path, following NextToken.
- Log how many config values were provided by each source.
- Add support for continuous SSM polling for dynamic reconfiguration.
- Add `populate-from-map` function.

### 0.3.2 (2018-08-20)

Stop depending on Amazonica, use AWS Java SDK directly.

### 0.3.1 (2018-05-24)

Removed all reflection warnings.

### 0.3.0 (2018-02-19)

- Added [SSM
  provider](https://github.com/grammarly/omniconf#fetching-configuration-from-aws-systems-manager-ssm)
  to fetch values from AWS System Manager's parameter store.
- Passing `quit-on-error` argument is not needed now. Omniconf will
  automatically detect when it's not running in a REPL and will trim the
  stacktrace in case of an error to reduce the noise in the output.
- As a result, `quit-on-error` arity for `populate-from-*` functions are now
  deprecated and will be removed in version 0.4.0.

### 0.2.8 (2018-02-15)

- [#9](https://github.com/grammarly/omniconf/issues/9) Fix bug with nested
  delayed transform not triggering.

### 0.2.7 (2018-02-13)

- [#10](https://github.com/grammarly/omniconf/issues/10) Fix bug with
  `populate-from-file` not working correctly with nested values.

### 0.2.6 (2018-02-13)

- [#5](https://github.com/grammarly/omniconf/issues/5) Correctly handle `false`
  as boolean value when explicitly set.
