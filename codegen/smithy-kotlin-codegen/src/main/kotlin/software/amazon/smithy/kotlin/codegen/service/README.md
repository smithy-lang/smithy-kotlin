# Smithy Kotlin Service Codegen (SKSC)

## Overview

This project generate **service-side code** from Smithy models, producing **complete service stubs**, including routing, serialization/deserialization, authentication, and validation, so developers can focus entirely on implementing business logic.

While Ktor is the default backend, the architecture is framework-agnostic, allowing future support for other server frameworks.


## Getting Started

- Get an [introduction to Smithy](https://smithy.io/2.0/index.html)
- Follow [Smithy's quickstart guide](https://smithy.io/2.0/quickstart.html)
- See the [Guide](docs/GettingStarted.md) to learn how to use SKSC to generate service.
- See a [Summary of Service Support](docs/Summary.md) to learn which features are supported


## Development

### Module Structure

- `constraints` – directory that contains the constraints validation generator.
  - `ConstraintsGenerator.kt` - main generator for constraints.
  - `ConstraintUtilsGenerator` - generator for constraint utilities.
  - For each constraint trait, there is a dedicated file.
- `ktor` – directory that stores all features generators specific to Ktor.
  - `ktorStubGenerator.kt` – main generator for ktor framework service stub generator.
- `ServiceStubConfiguration.kt` – configuration file for the service stub generator.
- `ServiceStubGenerator.kt` – abstract service stub generator file.
- `ServiceTypes.kt` – file that includes service component symbols.
- `utils.kt` – utilities file.

### Testing

The **service code generation tests** are located in `tests/codegen/service-codegen-tests`. These end-to-end tests generate the service, launch the server, send HTTP requests to validate functionality, and then shut down the service once testing is complete. This process typically takes around 2 minutes. To run tests specifically for SKSC, use the following command:
```bash
  ./gradlew :tests:codegen:service-codegen-tests:test
```

## Feedback

You can provide feedback or report a bug by submitting an [issue](https://github.com/smithy-lang/smithy-kotlin/issues/new/choose).
This is the preferred mechanism to give feedback so that other users can engage in the conversation, +1 issues, etc.