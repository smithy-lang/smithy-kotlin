# <img alt="Smithy" src="https://github.com/smithy-lang/smithy/blob/main/docs/_static/favicon.png?raw=true" width="28"> Smithy Kotlin

---

[Smithy](https://smithy.io/2.0/index.html) code generators for [Kotlin](https://kotlinlang.org/).

## Getting Started
- Get an [introduction to Smithy](https://smithy.io/2.0/index.html)
- Follow [Smithy's quickstart guide](https://smithy.io/2.0/quickstart.html)
- Apply the Smithy Gradle plugin to your project and start generating Kotlin code! 

## Development

### Module Structure

* `codegen` - module(s) for generating Kotlin code from Smithy models
    * `protocol-tests` - module for generating Smithy protocol tests 
    * `smithy-aws-kotlin-codegen` - module containing AWS-specific codegen, will eventually be refactored to `aws-sdk-kotlin`
    * `smithy-kotlin-codegen` - primary codegen module
    * `smithy-kotlin-codegen-testutils` - utilities for testing generated code (shared with `aws-sdk-kotlin`)

* `runtime` - library code used by generated clients and servers to perform SDK functions
  * `auth` - authentication and signing related modules
  * `crt-util` - utilities for using the AWS Common Runtime (CRT)
  * `observability` - contains various telemetry provider implementations
  * `protocol` - protocol support (including HTTP, application level protocols, test support, etc)
  * `runtime-core` - contains core functionality used by all clients, servers, or other runtime modules
  * `serde` - serialization/deserialization modules
  * `smithy-client` - runtime support for generated service clients
  * `smithy-test` - runtime support for generated tests (e.g. smithy protocol tests)
  * `testing` - internal testing utilities for the runtime

* `tests` - test and benchmark module(s)
    * `benchmarks` - benchmarks for runtime
    * `codegen` - codegen integration tests for various features (e.g. testing waiters, paginators, etc)
    * `compile` - compile tests for generated code
    * `integration` - tests for different versions of our dependencies to ensure compatibility

## Feedback

You can provide feedback or report a bug by submitting an [issue](https://github.com/smithy-lang/smithy-kotlin/issues/new/choose).
This is the preferred mechanism to give feedback so that other users can engage in the conversation, +1 issues, etc.


## Contributing 

If you are interested in contributing to Smithy Kotlin, please take a look at [CONTRIBUTING](CONTRIBUTING.md).


## License

This project is licensed under the Apache-2.0 License.


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

