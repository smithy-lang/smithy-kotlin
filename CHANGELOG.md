# Changelog

## [0.7.1-alpha] - 11/04/2021

**WARNING: Alpha releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### New features
* add convenience extension method for detecting if a Shape has the Streaming trait [#512](https://github.com/awslabs/smithy-kotlin/pull/512)

### Fixes
* delegate when to set content-type to binding resolver; fix content-length for empty bodies [#525](https://github.com/awslabs/smithy-kotlin/pull/525)
* Update regex to handle version segments greater than 9 [#521](https://github.com/awslabs/smithy-kotlin/pull/521)

## [0.7.0-alpha] - 10/28/2021

**WARNING: Alpha releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### New Features

* add endpoint configuration and middleware by default [#507](https://github.com/awslabs/smithy-kotlin/pull/507)

## [0.6.0-alpha] - 10/21/2021

**WARNING: Alpha releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### Fixes
* emulate a real response body more closely to help catch subtle codegen bugs [#505](https://github.com/awslabs/smithy-kotlin/pull/505)
* **BREAKING**: overhaul client config property generation [#504](https://github.com/awslabs/smithy-kotlin/pull/504)

### Misc
* Bump Kotlin and Dokka versions to latest release [#506](https://github.com/awslabs/smithy-kotlin/pull/506)

## [0.5.1-alpha] - 10/14/2021

**WARNING: Alpha releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### New features

* http client engine config [#493](https://github.com/awslabs/smithy-kotlin/pull/493)
* add codegen wrappers for retries [#490](https://github.com/awslabs/smithy-kotlin/pull/490)

### Fixes

* remove gson from dependencies [#496](https://github.com/awslabs/smithy-kotlin/pull/496)

