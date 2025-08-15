# Smithy Kotlin Service Codegen (Ktor)

## Overview

This project extends **Smithy Kotlin** to generate **service-side code** from Smithy models, targeting the **Ktor** framework for server implementation.  
It produces **complete service stubs**—including routing, serialization/deserialization, authentication, and validation—so developers can focus entirely on implementing business logic.

While Ktor is the default backend, the architecture is framework-agnostic, allowing future support for other server frameworks.

---

## Feature Summary

| **Features**              | **Description** |
|---------------------------|-----------------|
| Service Framework         | Abstracted service framework interface and base implementation with Ktor as the default backend |
| CBOR Protocol             | Support for CBOR serialization / deserialization and CBOR protocol traits |
| Json Protocol             | Support for Json serialization / deserialization and Json protocol traits |
| Routing                   | Per-operation routing generation with Ktor DSL; ties to handler and validation |
| Error Handler             | Unified exception handling logic mapped to HTTP status codes and support for error trait |
| Authentication (bearer)   | Bearer token authentication middleware with model-driven configuration |
| Logging                   | Structured logging setup |
| Constraints Checker       | Validation logic generated from Smithy traits and invoked pre-handler |
| Unit Test                 | Covers serialization/deserialization, routing, validation, and integration tests |

---

## Smithy Protocol Traits Support

| **Traits**               | **CBOR Protocol** | **Json Protocol** |
|--------------------------|-------------------|-------------------|
| http                     | Yes               | Yes               |
| httpError                | Yes               | Yes               |
| httpHeader               | Not supported     | Yes               |
| httpPrefixHeader         | Not supported     | Yes               |
| httpLabel                | Not supported     | Yes               |
| httpQuery                | Not supported     | Yes               |
| httpQueryParams          | Not supported     | Yes               |
| httpPayload              | Not supported     | Yes               |
| jsonName                 | Not supported     | Yes               |
| timestampFormat          | Not supported     | Yes               |
| httpChecksumRequired     | Not supported     | Not implemented yet |
| requestCompression       | Not implemented yet | Not implemented yet |

---

## Constraint Traits Support

| **Traits**      | **CBOR Protocol**            | **Json Protocol**            |
|-----------------|------------------------------|------------------------------|
| required        | Yes                          | Yes                          |
| length          | Yes                          | Yes                          |
| pattern         | Yes                          | Yes                          |
| private         | Yes (handled by Smithy)      | Yes (handled by Smithy)      |
| range           | Yes                          | Yes                          |
| uniqueItems     | Yes                          | Yes                          |
| idRef           | Not implemented yet          | Not implemented yet          |

---

## Getting Started

### Step 1: Build & Publish Codegen to Local Maven
First, in **this repository**, build and publish the code generator locally:
```bash
./gradlew :codegen:smithy-kotlin-codegen:build
./gradlew publishToMavenLocal
```

---

### Step 2: Create a New Kotlin Project
Now, create a **new Kotlin project** where you will use the Smithy Kotlin service code generator.

From this point forward, **all steps apply to the new Kotlin project** you just created.

---

### Step 3: Configure `build.gradle.kts` in the New Project

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("software.amazon.smithy.gradle.smithy-jar") version "1.3.0" // check for latest version
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    smithyBuild("software.amazon.smithy.kotlin:smithy-kotlin-codegen:<codegenVersion>")
    implementation("software.amazon.smithy.kotlin:smithy-aws-kotlin-codegen:<codegenVersion>")
    implementation("software.amazon.smithy:smithy-model:<smithyVersion>")
    implementation("software.amazon.smithy:smithy-build:<smithyVersion>")
    implementation("software.amazon.smithy:smithy-aws-traits:<smithyVersion>")
    ...
}
```

---

### Step 4: Create `smithy-build.json` in the New Project
This is an example of smithy-build.json.
```json
{
    "version": "1.0",
    "outputDirectory": "build/generated-src", // define the output path
    "plugins": {
        "kotlin-codegen": {
          // change based on smithy model <namespace>#<service shape name>
            "service": "com.demo#DemoService",
            "package": {
                "name": "com.demo.server",
                "version": "1.0.0"
            },
            "build": {
                "rootProject": true,
                "generateServiceProject": true,
                "optInAnnotations": [
                    "aws.smithy.kotlin.runtime.InternalApi",
                    "kotlinx.serialization.ExperimentalSerializationApi"
                ]
            },
            "serviceStub": {
              // choose server framework, only ktor is supported now
              "framework": "ktor"
            }
        }
    }
}
```

---

### Step 5: Define Your Smithy Model in the New Project

Create a `model` directory and add your `.smithy` files.  
Example `model/greeter.smithy`:

```smithy
$version: "2.0"
namespace com.demo

use aws.protocols#restJson1
use smithy.api#httpBearerAuth

@restJson1
@httpBearerAuth
service DemoService {
    version: "1.0.0"
    operations: [
        SayHello
    ]
}

@http(method: "POST", uri: "/greet", code: 201)
operation SayHello {
    input: SayHelloInput
    output: SayHelloOutput
    errors: [
        CustomError
    ]
}

@input
structure SayHelloInput {
    @required
    @length(min: 3, max: 10)
    name: String
    @httpHeader("X-User-ID")
    id: Integer
}

@output
structure SayHelloOutput {
    greeting: String
}

@error("server")
@httpError(500)
structure CustomError {
    msg: String
    @httpHeader("X-User-error")
    err: String
}
```

---

### Step 6: Generate the Service in the New Project

Run:
```bash
gradle build run
```

If you want to clean previously generated code:
```bash
gradle clean
```

---

### Step 7: Run the Generated Service

The generated service will be in the directory specified in `smithy-build.json` (`outputDirectory`).  
You can start it by running:
```bash
gradle run
```
By default, it listens on port **8080**.

---

### Step 8: Adjust Service Configuration

You can override runtime settings (such as port or HTTP engine) using command-line arguments:
```bash
gradle run --args="port 8000 engineFactory cio"
```

---

## Notes
- **Business Logic**: Implement your own logic in the generated operation handler interfaces.
- **Configuration**: Adjust port, engine, auth, and other settings via `ServiceFrameworkConfig` or CLI args.
- **Future Extensions**: Planned support for more serialization formats (JSON, XML) and AWS SigV4 auth.
