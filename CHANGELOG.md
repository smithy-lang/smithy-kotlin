# Changelog

## [0.12.4] - 08/11/2022

### Miscellaneous
* Upgrade Kotlin version to 1.7.10
* Bump CRT version.

## [0.12.2] - 08/04/2022

### Fixes
* [#665](https://github.com/awslabs/aws-sdk-kotlin/issues/665) Parse timestamps correctly when they are written in exponential notation (e.g., `1.924390954E9`)

## [0.12.1] - 07/21/2022

### Miscellaneous
* Enable [Explicit API mode](https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md)

## [0.12.0] - 07/14/2022

### Fixes
* **Breaking**: Generate `List<T>` members for all collection types. (Previously, `Set<T>` would be generated for lists decorated with `@uniqueItems`.)
* **Breaking**: Move DSL overloads on generated clients to extension methods

## [0.11.2] - 07/08/2022

### Features
* Add support for NOT, OR, and AND JMESPath expressions in waiters
* [#123](https://github.com/awslabs/smithy-kotlin/issues/123) Add support for smithy Document type.

### Miscellaneous
* [#599](https://github.com/awslabs/smithy-kotlin/issues/599) Upgrade Smithy version to 1.22

## [0.11.1] - 07/01/2022

### Features
* Add support for HTTP_REQUEST_EVENT chunked signing

### Miscellaneous
* Upgrade Kotlin to 1.7

## [0.11.0] - 06/23/2022

### Features
* (breaking) Use kotlin nullability to represent null Documents instead of an explicit subclass.
* [#494](https://github.com/awslabs/smithy-kotlin/issues/494) Add support for HTTP proxies

### Fixes
* [#638](https://github.com/awslabs/aws-sdk-kotlin/issues/638) Fix ktor engine representation of empty payload
* [#139](https://github.com/awslabs/smithy-kotlin/issues/139) Validate that members bound to URI paths are non-null at object construction

### Miscellaneous
* [#629](https://github.com/awslabs/smithy-kotlin/issues/629) Refactor to bind directly to okhttp and remove ktor as a middleman

## [0.10.2] - 06/09/2022

### Fixes
* [#619](https://github.com/awslabs/aws-sdk-kotlin/issues/619) Fix bugs with signing for query parameters containing '+' and '%'
* [#657](https://github.com/awslabs/smithy-kotlin/issues/657) Fix bug in URI encoding during signing when dealing with special characters like '<', '>', and '/'

## [0.10.1] - 06/02/2022

### Features
* [#617](https://github.com/awslabs/smithy-kotlin/issues/617) Add a new non-CRT SigV4 signer and use it as the default. This removes the CRT as a hard dependency for using the SDK (although the CRT signer can still be used via explicit configuration on client creation).

### Fixes
* [#473](https://github.com/awslabs/aws-sdk-kotlin/issues/473) Upgrade aws-crt-kotlin to latest

## [0.10.0] - 05/24/2022

### Features
* add additional trace logging to default HTTP client engine
* [#460](https://github.com/awslabs/aws-sdk-kotlin/issues/460) Enhance generic codegen to be more KMP-friendly

### Fixes
* [#480](https://github.com/awslabs/aws-sdk-kotlin/issues/480) Upgrade to ktor-2.x

### Miscellaneous
* upgrade kotlin to 1.6.21 and other deps to latest

## [0.9.2] - 05/19/2022

### Features
* [#129](https://github.com/awslabs/smithy-kotlin/issues/129) Allow omission of input in service method calls where no parameters are required.

### Fixes
* Handle invalid (empty) description term headers when generating documentation.
* Don't escape markdown within preformat blocks in documentation.

## [0.9.1] - 05/13/2022

### Features
* [#393](https://github.com/awslabs/aws-sdk-kotlin/issues/393) Add convenience getters for union members
* [#530](https://github.com/awslabs/aws-sdk-kotlin/issues/530) Add partial-file ByteStream support.

### Fixes
* [#136](https://github.com/awslabs/smithy-kotlin/issues/136) Convert HTML to Markdown for improved Dokka compatibility.

### Miscellaneous
* [#575](https://github.com/awslabs/aws-sdk-kotlin/issues/575) Add support for getting all env vars and system properties (i.e., not just by a single key)
* Expose Byte.percentEncodeTo for downstream recursion detection.

## [0.9.0] - 04/29/2022

### Miscellaneous
* Refactor hashing functions into new subproject

## [0.8.5] - 04/21/2022

### Fixes
* set Content-Type header on empty bodies with Ktor if canonical request includes it [#630](https://github.com/awslabs/smithy-kotlin/pull/630)
* coroutine leak in ktor engine [#628](https://github.com/awslabs/smithy-kotlin/pull/628)

## [0.8.4] - 04/21/2022

NOTE: Do not use. No difference from 0.8.3

## [0.8.3] - 04/14/2022

### Fixes
* only set Content-Type when body is non-empty [#619](https://github.com/awslabs/smithy-kotlin/pull/619)

## [0.8.2] - 04/07/2022

### Fixes
* remove maxTime from StandardRetryStrategy [#624](https://github.com/awslabs/smithy-kotlin/pull/624)
* readAvailable fallback behavior [#620](https://github.com/awslabs/smithy-kotlin/pull/620)

## [0.8.1] - 03/31/2022

### New features
* implement KMP XML serde and remove XmlPull dependency [#615](https://github.com/awslabs/smithy-kotlin/pull/615)

### Fixes
* respect hostname immutability in endpoints to disable host prefixing [#612](https://github.com/awslabs/smithy-kotlin/pull/612)

## [0.8.0] - 03/24/2022

### Breaking changes
* introduce opaque KMP default HTTP client engine [#606](https://github.com/awslabs/smithy-kotlin/pull/606)

### New features
* bootstrap event streams [#597](https://github.com/awslabs/smithy-kotlin/pull/597)

### Fixes
* temporarily handle httpchecksum trait the same as httpchecksumrequired [#608](https://github.com/awslabs/smithy-kotlin/pull/608)

### Miscellaneous
* add convenience function for decoding URL components [#607](https://github.com/awslabs/smithy-kotlin/pull/607)
* fix pagination design docs [#600](https://github.com/awslabs/smithy-kotlin/pull/600)

## [0.7.8] - 02/17/2022

### New features
* add DSL overloads to paginator methods [#591](https://github.com/awslabs/smithy-kotlin/pull/591)

### Fixes
* ignore redirect statuses in Ktor engine [#590](https://github.com/awslabs/smithy-kotlin/pull/590)
* handle stream response exceptions properly in Ktor engine [#589](https://github.com/awslabs/smithy-kotlin/pull/589)

### Miscellaneous
* coroutine version bump to 1.6.0 and Duration stabilization [#580](https://github.com/awslabs/smithy-kotlin/pull/580)
* dokka upgrade [#588](https://github.com/awslabs/smithy-kotlin/pull/588)
* upgrade smithy to 1.17.0 [#587](https://github.com/awslabs/smithy-kotlin/pull/587)

## [0.7.7] - 02/04/2022

### New features
* implement waiters codegen [#584](https://github.com/awslabs/smithy-kotlin/pull/584)
* Kotlin MP gradle file generation [#577](https://github.com/awslabs/smithy-kotlin/pull/577)
* allow custom rendering of config properties [#568](https://github.com/awslabs/smithy-kotlin/pull/568)

### Miscellaneous
* event stream design [#576](https://github.com/awslabs/smithy-kotlin/pull/576)

## [0.7.6] - 01/13/2022

### New features
* Paginator codegen using Kotlin Flow [#557](https://github.com/awslabs/smithy-kotlin/pull/557)
* allow nullable LazyAsyncValue; add test utils for constructing http traffic from JSON [#561](https://github.com/awslabs/smithy-kotlin/pull/561)

### Fixes
* paginator generation for models that specify non-string cursor [#566](https://github.com/awslabs/smithy-kotlin/pull/566)
* Fix smithy sdk no default client [#564](https://github.com/awslabs/smithy-kotlin/pull/564)

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

