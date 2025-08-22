# Getting Started

### Step 1: Build & Publish Codegen to Local Maven
First, in **this repository**, build and publish the code generator locally:
```bash
  ./gradlew :codegen:smithy-kotlin-codegen:build
  ./gradlew publishToMavenLocal
```

### Step 2: Create a New Kotlin Project
Now, create a **new Kotlin project** where you will use the Smithy Kotlin service code generator. You can find a full example demo project [here](../../../../../../../../../../../../examples/service-codegen)

From this point forward, **all steps apply to the new Kotlin project** you just created.


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

val codegenVersion = "0.35.2-SNAPSHOT"
val smithyVersion = "1.60.2"

dependencies {
    smithyBuild("software.amazon.smithy.kotlin:smithy-kotlin-codegen:$codegenVersion")
    implementation("software.amazon.smithy.kotlin:smithy-aws-kotlin-codegen:$codegenVersion")
    implementation("software.amazon.smithy:smithy-model:$smithyVersion")
    implementation("software.amazon.smithy:smithy-build:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    ...
}
```


### Step 4: Create `smithy-build.json` in the New Project
This is an example of smithy-build.json.
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

**Notes:**
- The most important fields are:
    - **`outputDirectory`** — defines where the generated service code will be placed in your new project.
    - **`service`** — must match your Smithy model’s `<namespace>#<service shape name>`.
    - **`serviceStub.framework`** — defines the server framework for generated code. Currently only `"ktor"` is supported.

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

### Step 6: Generate the Service in the New Project

Run:
```bash
  gradle build
```

⚠️ Running gradle build will delete the previous build output before creating a new one.

If you want to prevent accidentally losing previous build, use the provided scripts instead:

You can find script for Linux / macOS [here](../../../../../../../../../../../../examples/service-codegen/build.sh):
```bash
  chmod +x build.sh
  ./build.sh
```

You can find script for Windows [here](../../../../../../../../../../../../examples/service-codegen/build.bat):
```bash
  icacls build.bat /grant %USERNAME%:RX
  .\build.bat
```

If you want to clean previously generated code:
```bash
  gradle clean
```

### Step 7: Run the Generated Service

The generated service will be in the directory specified in `smithy-build.json` (`outputDirectory`).  
You can start it by running:
```bash
  gradle run
```
By default, it listens on port **8080**.

### Step 8: Adjust Service Configuration

You can override runtime settings (such as port or HTTP engine) using command-line arguments:
```bash
  gradle run --args="port 8000 engineFactory cio"
```
You can find all available settings [here](https://github.com/smithy-lang/smithy-kotlin/blob/16bd523e2ccd6177dcc662466107189b013a818d/codegen/smithy-kotlin-codegen/src/main/kotlin/software/amazon/smithy/kotlin/codegen/service/ServiceStubGenerator.kt#L179C1-L186C38)

---

## Notes
- **Business Logic**: Implement your own logic in the generated operation handler interfaces.
- **Configuration**: Adjust port, engine, auth, and other settings via `ServiceFrameworkConfig` or CLI args.
