# Changelog

## [0.21.1] - 06/01/2023

### Fixes
* Allow empty I/O content

### Miscellaneous
* Support non-standard pagination termination

## [0.21.0] - 05/25/2023

### Features
* [#755](https://github.com/awslabs/smithy-kotlin/issues/755) **Breaking**: Refresh presigning APIs to simplify usage and add new capabilities. See [this discussion post](https://github.com/awslabs/aws-sdk-kotlin/discussions/925) for more information.

## [0.20.0] - 05/18/2023

### Features
* **Breaking**: Make HTTP engines configurable in client config during initialization and during `withCopy`. See [this discussion post](https://github.com/awslabs/aws-sdk-kotlin/discussions/919) for more information.

## [0.19.0] - 05/12/2023

### Features
* Add support for bearer token auth schemes
* Add support for writing a file via PlatformProvider

### Fixes
* Fix usage of precalculated checksum values

### Miscellaneous
* Refactor CredentialsProviderChain into generic/re-usable IdentityProviderChain

## [0.18.0] - 05/04/2023

### Features
* [#661](https://github.com/awslabs/smithy-kotlin/issues/661) **Breaking**: Add HTTP engine configuration for minimum TLS version. See the [BREAKING: Streamlined TLS configuration](https://github.com/awslabs/aws-sdk-kotlin/discussions/909) discussion post for more details.
* BREAKING: rename SdkLogMode to LogMode
* [#432](https://github.com/awslabs/aws-sdk-kotlin/issues/432) Enable resolving LogMode from environment

### Fixes
* Fix incorrect waiter codegen due to dropped projection scope
* Fix broken shape cursor when generating acceptor subfield projections.

## [0.17.4] - 04/27/2023

### Fixes
* [#892](https://github.com/awslabs/aws-sdk-kotlin/issues/892) Fix broken enum-based waiters.
* Fix okhttp streaming body failing to retry

### Miscellaneous
* Refactor environment settings and retry modes out of aws-sdk-kotlin

## [0.17.3] - 04/20/2023
This release is identical to 
[0.17.2](https://github.com/awslabs/smithy-kotlin/blob/main/CHANGELOG.md#0172---04202023).

## [0.17.2] - 04/20/2023

### Features
* Add support for retrying transient HTTP errors. `RetryErrorType.Timeout` was renamed to `RetryErrorType.Transient`.
* Enhance exceptions thrown during proxy config parsing
* Add support for H2_PRIOR_KNOWLEDGE (HTTP2 without TLS)

### Miscellaneous
* Refactor endpoint resolution to be explicit in SdkOperationExecution and change order to match SRA.

## [0.17.1] - 04/13/2023

### Fixes
* Only take contentLength if it's >= 0 in CRT HTTP engine

## [0.17.0] - 04/13/2023

### Fixes
* [#818](https://github.com/awslabs/smithy-kotlin/issues/818) Allow requests with zero content length in the CRT HTTP engine

### Miscellaneous
* **BREAKING**: Refactor identity and authentication APIs

## [0.16.6] - 04/06/2023

### Features
* [#752](https://github.com/awslabs/smithy-kotlin/issues/752) Add intEnum support.

## [0.16.5] - 03/30/2023

### Features
* Add code support for awsQuery-compatible error responses.

### Miscellaneous
* Add clarifying documentation for `endpointProvider` in client config.

## [0.16.4] - 03/16/2023

### Fixes
* [#868](https://github.com/awslabs/aws-sdk-kotlin/issues/868) Fix a bug in presigned URL generation when using a ServicePresignConfig object

## [0.16.3] - 03/09/2023

### Features
* Add configurable reader-friendly name to generated SDK clients.

## [0.16.1] - 03/02/2023

### Fixes
* [#862](https://github.com/awslabs/aws-sdk-kotlin/issues/862) Skip signing the `Expect` header in SigV4

## [0.16.0] - 02/23/2023

### Fixes
* [#805](https://github.com/awslabs/smithy-kotlin/issues/805) Fix a bug where system time jumps could cause unexpected retry behavior

### Miscellaneous
* Refactor: move `EndpointProvider` out of http package into `aws.smithy.kotlin.runtime.client.endpoints`
* Refactor: relocate `CachedCredentialsProvider` and `CredentialsProviderChain` from `aws-sdk-kotlin`
* Refactor: move `Document` type to `aws.smithy.kotlin.runtime.content` package

## [0.15.3] - 02/16/2023

### Features
* [#839](https://github.com/awslabs/aws-sdk-kotlin/issues/839) Add an interceptor for adding `Expect: 100-continue` headers to HTTP requests

### Miscellaneous
* Upgrade to Kotlin 1.8.10

## [0.15.2] - 02/09/2023

### Features
* Add readFully extension method to SdkSource
* Add additional tracing events for connections in CRT engines
* Add new `maxConnectionsPerHost` configuration setting for OkHttp engines
* Add configuration for retry policy on clients

### Fixes
* Stop logging "null" when exceptions are not present in trace events
* Correctly apply `maxConnections` configuration setting to OkHttp engines

### Miscellaneous
* Refactor: break out service client runtime components into own module
* Refactor: split client side HTTP abstractions into new module. Move Url into core

## [0.15.1] - 02/02/2023

### Features
* [#446](https://github.com/awslabs/smithy-kotlin/issues/446) Implement flexible checksums customization
* Add support for unsigned `aws-chunked` requests

### Miscellaneous
* Refactor: collapse io, hashing, and util modules into runtime-core module

## [0.15.0] - 01/27/2023

### Features
* Allow config override for one or more operations with an existing service client.

### Fixes
* [#781](https://github.com/awslabs/smithy-kotlin/issues/781) Lazily open random access files to prevent exhausting file handles in highly concurrent scenarios
* [#784](https://github.com/awslabs/smithy-kotlin/issues/784) Include exceptions in logging from trace probes

### Miscellaneous
* Upgrade dependencies
* **Breaking** Remove `Closeable` supertype from `HttpClientEngine` interface. See [this discussion post](https://github.com/awslabs/aws-sdk-kotlin/discussions/818) for more information.
* Upgrade Kotlin version to 1.8.0
* Refactor the way service client configuration is generated

## [0.14.3] - 01/12/2023

### Features
* [#122](https://github.com/awslabs/smithy-kotlin/issues/122) Add capability to intercept SDK operations
* [#745](https://github.com/awslabs/smithy-kotlin/issues/745) Add KMP DNS resolver

### Miscellaneous
* Add design document for per-op config.

## [0.14.2] - 12/22/2022

### Fixes
* (**runtime**) Fix incorrect CRC32c output when trying to hash more than 7 bytes

### Miscellaneous
* Move test utilities out of **smithy-kotlin-codegen** package into new **smithy-kotlin-codegen-testutils** package. This eliminates the need for the codegen package to declare runtime dependencies on JUnit and other test packages.

## [0.14.1] - 12/15/2022

### Features
* Add HashingSource and HashingSink
* Use `aws-chunked` content encoding for streaming requests

### Fixes
* [#759](https://github.com/awslabs/aws-sdk-kotlin/issues/759) Allow root trace spans to inherit their parent from current context
* [#763](https://github.com/awslabs/smithy-kotlin/issues/763) Respect @sensitive trait when applied to container shape
* [#759](https://github.com/awslabs/smithy-kotlin/issues/759) Fix `aws-chunked` requests in the CRT HTTP engine

## [0.14.0] - 12/01/2022

### Miscellaneous
* **BREAKING** Refactor SDK I/O types. See [this discussion post](https://github.com/awslabs/aws-sdk-kotlin/discussions/768) for more information

## [0.13.1] - 11/23/2022

### Fixes
* Fix deserialization error for shapes with lists of document types

## [0.13.0] - 11/22/2022

### Features
* **BREAKING** Implement codegen and update runtime for smithy-modeled endpoint resolution.
* [#677](https://github.com/awslabs/smithy-kotlin/issues/677) Add a new tracing framework for centralized handling of log messages and metric events and providing easy integration points for connecting to downstream tracing systems (e.g., kotlin-logging)
* [#747](https://github.com/awslabs/aws-sdk-kotlin/issues/747) Add aws-chunked content encoding
* Implement common-Kotlin URL parsing and IPv4/v6 address validation.

### Fixes
* Remove erroneous `@InternalApi` marker on CRT HTTP engine configuration class

### Miscellaneous
* Allow using maven local for to test Smithy changes

## [0.12.13] - 11/15/2022

### Fixes
* [#753](https://github.com/awslabs/aws-sdk-kotlin/issues/753) Fix Android crash when OkHttp response body coroutine throws an exception

## [0.12.12] - 11/10/2022

### Fixes
* Require values for HTTP query- and queryParams-bound parameters

## [0.12.11] - 11/03/2022

### Features
*  Add CRC32C hashing algorithm

### Fixes
* [#733](https://github.com/awslabs/aws-sdk-kotlin/issues/733) Fix OkHttp engine crashing on Android when coroutine is cancelled while uploading request body
* Correct formurl serialization of empty lists

## [0.12.10] - 10/27/2022

### Features
* add `MIN` and `MAX` accessors for the Instant type
*  Add order parameter to ClientConfigProperty to be used for ordering configuration dependencies

### Fixes
* [#733](https://github.com/awslabs/aws-sdk-kotlin/issues/733) Fix OkHttp engine crashing on Android when coroutine is cancelled while uploading request body

## [0.12.9] - 10/13/2022

### Fixes
* [#715](https://github.com/awslabs/aws-sdk-kotlin/issues/715) Enable intra-repo links in API ref docs
* [#714](https://github.com/awslabs/smithy-kotlin/issues/714) Properly parse timestamps when format override is applied to target shapes

## [0.12.8] - 10/03/2022

### Fixes
* Fix inconsistent nullability semantics when generating struct members.

## [0.12.7] - 09/29/2022

### Features
* [#486](https://github.com/awslabs/aws-sdk-kotlin/issues/486) Enable configurability of the retry strategy through environment variables, system properties, and AWS profiles.

### Fixes
* Switch to a safer check to determine if all bytes have been read from an HTTP body
* [#704](https://github.com/awslabs/aws-sdk-kotlin/issues/704) Disable throwing CancellationException in OkHttp engine's transferBody method

### Miscellaneous
* Update/clarify changelog and commit instructions in the Contributing Guidelines

## [0.12.6] - 09/19/2022

### Features
* Add support for full duplex HTTP exchanges

### Fixes
* Fix occasional stream leak due to race condition in CRT engine
* [#678](https://github.com/awslabs/smithy-kotlin/issues/678) Fix the calculation of file lengths on `ByteStream`s from `Path`s
* Properly check if a member can be nullable

### Miscellaneous
* Provide an explicit scope for request bound work

## [0.12.5] - 08/18/2022

### Fixes
* [#55](https://github.com/awslabs/aws-crt-kotlin/issues/55) Upgrade aws-crt-kotlin dependency to fix Mac dlopen issue
* [#601](https://github.com/awslabs/aws-sdk-kotlin/issues/601) Remove incorrect `.` at end of license identifier header in source files.

### Documentation
* [#683](https://github.com/awslabs/aws-sdk-kotlin/issues/683) Enhance **CONTRIBUTING.md** with additional details about required PR checks and how to run them locally

### Miscellaneous
* Upgrade Smithy to 1.23.0, upgrade Smithy Gradle to 0.6.0
* Upgrade ktlint to 0.46.1.
* Fix ktlint issues and enable ktlint to run on all pull requests (even from forked repos)

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

