# Serde Benchmarks

This project contains micro benchmarks for the serialization implementation(s).

## Testing

```sh
./gradlew :runtime:serde:serde-benchmarks:jvmBenchmark
```

Baseline `0.4.0-alpha`

```
jvm summary:
Benchmark                              Mode  Cnt  Score   Error  Units
CitmBenchmark.tokensBenchmark          avgt    5  6.060 ± 0.549  ms/op
TwitterBenchmark.deserializeBenchmark  avgt    5  6.433 ± 0.396  ms/op
TwitterBenchmark.serializeBenchmark    avgt    5  1.551 ± 0.090  ms/op
TwitterBenchmark.tokensBenchmark       avgt    5  4.375 ± 0.080  ms/op
```

## JSON Data
Raw data was imported from [nativejson-benchmark](https://github.com/miloyip/nativejson-benchmark).

JSON file   | Size | Description
------------|------|-----------------------
`citm_catalog.json` [source](https://github.com/RichardHightower/json-parsers-benchmark/blob/master/data/citm_catalog.json) | 1737KB | A big benchmark file with indentation used in several Java JSON parser benchmarks.
`twitter.json` | 632KB | Search "一" (character of "one" in Japanese and Chinese) in Twitter public time line for gathering some tweets with CJK characters.


## Benchmarks

The `models` folder contains hand rolled Smithy models for some of the benchmarks. Code was generated in a standalone
project, hand massaged, and copied into the `model.{name}` folder. 

e.g.

```kotlin
plugins {
    kotlin("jvm") version "1.5.20"
    id("software.amazon.smithy").version("0.5.3")
}

repositories {
    mavenLocal()
    mavenCentral()
}

val smithyVersion = "1.9.1"

dependencies {
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    implementation("software.amazon.smithy.kotlin:smithy-aws-kotlin-codegen:0.4.0-alpha")
}

tasks["jar"].enabled = false
```

```json
{
    "version": "1.0",
    "plugins": {
        "kotlin-codegen": {
            "service": "aws.benchmarks.twitter#Twitter",
            "package": {
                "name": "aws.smithy.kotlin.benchmarks.serde.json.twitter",
                "version": "0.0.1"
            },
            "build": {
                "rootProject": true
            }
        }
    }
}
```

Copy the output into the appropriate directories, e.g.:

```shell
cp -r build/smithyprojections/smithy-sandbox/source/kotlin-codegen/src/main/kotlin/aws/smithy/kotlin/serde/benchmarks/json/twitter/model ~/path/to/smithy-kotlin/runtime/serde/serde-benchmarks/jvm/src/aws/smithy/kotlin/serde/benchmarks/json/twitter/.
cp -r build/smithyprojections/smithy-sandbox/source/kotlin-codegen/src/main/kotlin/aws/smithy/kotlin/serde/benchmarks/json/twitter/transform ~/path/to/smithy-kotlin/runtime/serde/serde-benchmarks/jvm/src/aws/smithy/kotlin/serde/benchmarks/json/twitter/.
```

Remove `GetFeedRequest`, `GetFeedResponse`, the operation serializer and deserializer, and the exception type.