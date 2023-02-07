## Smithy Kotlin

Smithy code generators for Kotlin.

**WARNING: All interfaces are subject to change.**

[![License][apache-badge]][apache-url]

[apache-badge]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[apache-url]: LICENSE

## Development

### Module Structure

* `codegen` - module(s) for generating Kotlin code from Smithy models
    * `smithy-kotlin-codegen` - primary codegen module
    * `smithy-kotlin-codegen-testutils` - utilities for testing generated code (shared with `aws-sdk-kotlin`)

* `tests`   - test and benchmark module(s)
    * `codegen` - codegen integration tests for various features (e.g. testing waiters, paginators, etc)
    * `compile` - compile tests for generated code
    * `benchmarks` - benchmarks for runtime

* `runtime` - library code used by generated clients and servers to perform SDK functions
    * `auth` - authentication and signing related modules
    * `protocol` - protocol support (including HTTP, application level protocols, test support, etc)
    * `runtime-core` - contains core functionality used by all clients, servers, or other runtime modules
    * `serde` - serialization/deserialization modules
    * `smithy-client` - runtime support for generated service clients
    * `smithy-test` - runtime support for generated tests (e.g. smithy protocol tests)
    * `testing` - internal testing utilities for the runtime


**What goes into `runtime-core`?**

Anything universally applicable to clients and servers alike OR consumed by large portions of the runtime. This includes things like
annotations, I/O, networking, time, hashing, etc. 


## License

This project is licensed under the Apache-2.0 License.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

