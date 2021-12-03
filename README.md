## Smithy Kotlin

Smithy code generators for Kotlin.

**WARNING: All interfaces are subject to change.**

[![License][apache-badge]][apache-url]

[apache-badge]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[apache-url]: LICENSE

## Development

### Modules

* `client-runtime` - library code used by generated clients to perform SDK functions
* `compile-tests` - a test module to verify that various codegen scenarios produce valid Kotlin source  
* `smithy-kotlin-codegen` - a module that generates Kotlin code from Smithy models

### Where Things Go

* Kotlin-language specific utility functions: `software.amazon.smithy.kotlin.codegen.lang`
* Smithy-based codegen utility functions: `smithy-kotlin-codegen/src/main/kotlin/software/amazon/smithy/kotlin/codegen/Utils.kt`

## License

This project is licensed under the Apache-2.0 License.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

