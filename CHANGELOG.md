# Changelog

## [0.7.5] - 01/06/2022

### New features
* upgrade to Kotlin 1.6.10 [#551](https://github.com/awslabs/smithy-kotlin/pull/551)
* allow query params to be set from resolved endpoints [#554](https://github.com/awslabs/smithy-kotlin/pull/554)
* sha1 hash; expose json utils [#548](https://github.com/awslabs/smithy-kotlin/pull/548)

### Fixes
* only retry client errors if metadata allows [#560](https://github.com/awslabs/smithy-kotlin/pull/560)

## [0.7.4-beta] - 12/09/2021

**WARNING: Beta releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### New features
* add call logging at DEBUG level [#547](https://github.com/awslabs/smithy-kotlin/pull/547)

## [0.7.3-beta] - 12/01/2021

**WARNING: Beta releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**


## [0.7.2-alpha] - 11/19/2021

**WARNING: Alpha releases may contain bugs and no guarantee is made about API stability. They are not recommended for production use!**

### Fixes
* Fix nested struct dsl builder function codegen [#538](https://github.com/awslabs/smithy-kotlin/pull/538)
* remove leaky trace logging from XML serde [#537](https://github.com/awslabs/smithy-kotlin/pull/537)
* fix rendering of middleware without configure [#535](https://github.com/awslabs/smithy-kotlin/pull/535)

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

