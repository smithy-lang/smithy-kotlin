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

The `model` folder contains hand rolled Smithy models for some of the benchmarks. The `smithy-benchmarks-codegen` project 
contains the codegen support to generate these models.

These models are generated as part of the build. Until you run `assemble` you may see errors in your IDE.