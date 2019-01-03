# Changelog

### master (unreleased)

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
