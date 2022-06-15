# HTTP Client Engine Benchmarks

This project contains benchmarks for the [HTTP engine implementations](../../../runtime/protocol/http-client-engines).

## Testing

```sh
./gradlew :tests:benchmarks:http-benchmarks:benchmark
```

Baseline `0.10.3-SNAPSHOT` on EC2 **[m5.4xlarge](https://aws.amazon.com/ec2/instance-types/m5/)** with **Corretto-11.0.15.9.1**:

The download/upload throughput benchmarks are an approximation of how much data in MB/s we are able to process.

```
jvm summary:
Benchmark                                      (httpClientName)   Mode  Cnt      Score     Error  Units
HttpEngineBenchmarks.downloadThroughputNoTls             OkHttp  thrpt    5    129.300 ±   1.796  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                CRT  thrpt    5    377.234 ±  10.801  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls        Ktor_OkHttp  thrpt    5    169.366 ±   2.480  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls            OkHttp  thrpt    5  33225.814 ± 209.392  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls               CRT  thrpt    5  28013.919 ± 455.085  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls       Ktor_OkHttp  thrpt    5  14736.287 ± 867.559  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls            OkHttp  thrpt    5   7801.946 ± 527.743  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls               CRT  thrpt    5   8712.930 ± 800.754  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls       Ktor_OkHttp  thrpt    5   4373.179 ±  90.326  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls               OkHttp  thrpt    5    198.005 ±   5.032  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                  CRT  thrpt    5    206.829 ±   5.680  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls          Ktor_OkHttp  thrpt    5    194.655 ±   4.665  ops/s
```

The `Ktor_OkHttp` engine is the Ktor wrapped OkHttp engine whereas the `OkHttp` engine is the raw binding to OkHttp
without Ktor.