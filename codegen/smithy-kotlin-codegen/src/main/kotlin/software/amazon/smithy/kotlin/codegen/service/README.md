# Smithy Kotlin Service Codegen (Ktor)

## Overview

This project extends **Smithy Kotlin** to generate **service-side code** from Smithy models, targeting the **Ktor** framework for server implementation.  
It produces **complete service stubs**—including routing, serialization/deserialization, authentication, and validation—so developers can focus entirely on implementing business logic.

While Ktor is the default backend, the architecture is framework-agnostic, allowing future support for other server frameworks.

### Key Features
- **Automatic Service Stub Generation** from Smithy models
- **Protocol Support**: CBOR & JSON
- **Request Routing** generated from Smithy operations
- **Authentication**: Bearer, SigV4, SigV4A
- **Request Constraints & Validation** from Smithy traits
- **Error Handling** with a consistent JSON/CBOR envelope
- **Logging** with structured output for production readiness
- **Extensible Architecture** for alternative frameworks

---

## Getting Started

### 1. Build & Publish to Local Maven
From the project root, run:
```bash
./gradlew :codegen:smithy-kotlin-codegen:build
./gradlew publishToMavenLocal
```

---

### 2. Create a New Kotlin Project

In your **`build.gradle.kts`**:

```kotlin
plugins {
id("software.amazon.smithy.gradle.smithy-jar") version "1.3.0" // check for latest version
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
}
```

---

### 3. Configure Smithy Build

Create `smithy-build.json` in the same directory as `build.gradle.kts`:

```json
{
"version": "1.0",
"outputDirectory": "build/generated-src",
"plugins": {
"kotlin-codegen": {
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
"framework": "ktor"
}
}
}
}
```

---

### 4. Define Your Smithy Model

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
operations: [SayHello]
}

@http(method: "POST", uri: "/greet", code: 201)
operation SayHello {
input: SayHelloInput
output: SayHelloOutput
errors: [CustomError]
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

### 5. Generate the Service

Run:
```bash
gradle build run
```

If you want to clean previously generated code:
```bash
gradle clean
```

---

### 6. Run the Service

The generated service will be in the directory specified in `smithy-build.json` (`outputDirectory`).  
You can start it by running:
```bash
gradle run
```
By default, it listens on port **8080**.

---

### 7. Adjust Service Configuration

You can override runtime settings (such as port or HTTP engine) using command-line arguments:
```bash
gradle run --args="port 8000 engineFactory cio"
```

---

## Notes
- **Business Logic**: Implement your own logic in the generated operation handler interfaces.
- **Configuration**: Adjust port, engine, auth, and other settings via `ServiceFrameworkConfig` or CLI args.
- **Future Extensions**: Planned support for more serialization formats (JSON, XML) and AWS SigV4 auth.
