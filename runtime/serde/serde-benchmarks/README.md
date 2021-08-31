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
CitmBenchmark.tokensBenchmark          avgt    5  7.188 ± 1.099  ms/op
TwitterBenchmark.deserializeBenchmark  avgt    5  5.999 ± 0.510  ms/op
TwitterBenchmark.tokensBenchmark       avgt    5  4.753 ± 1.337  ms/op
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
                "name": "aws.benchmarks.json",
                "version": "0.0.1"
            },
            "build": {
                "rootProject": true
            }
        }
    }
}
```