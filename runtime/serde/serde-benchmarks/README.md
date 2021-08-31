# Serde Benchmarks

This project contains micro benchmarks for the serialization implementation(s).

## Testing

```sh
./gradlew :runtime:serde:serde-benchmarks:jvmBenchmark
```

Baseline `0.4.0-alpha`
```
jvm summary:
Benchmark                         Mode  Cnt  Score   Error  Units
CitmBenchmark.tokensBenchmark     avgt    5  6.149 ± 0.733  ms/op
TwitterBenchmark.tokensBenchmark  avgt    5  4.611 ± 0.282  ms/op
```

## JSON Data
Raw data was imported from [nativejson-benchmark](https://github.com/miloyip/nativejson-benchmark).

JSON file   | Size | Description
------------|------|-----------------------
`citm_catalog.json` [source](https://github.com/RichardHightower/json-parsers-benchmark/blob/master/data/citm_catalog.json) | 1737KB | A big benchmark file with indentation used in several Java JSON parser benchmarks.
`twitter.json` | 632KB | Search "一" (character of "one" in Japanese and Chinese) in Twitter public time line for gathering some tweets with CJK characters.


