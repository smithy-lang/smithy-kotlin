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
HttpEngineBenchmarks.downloadThroughputNoTls             OkHttp  thrpt    5     10.728 ±   0.199  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                CRT  thrpt    5     31.411 ±   0.958  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls        Ktor_OkHttp  thrpt    5     14.030 ±   0.190  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls            OkHttp  thrpt    5  32295.186 ± 139.197  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls               CRT  thrpt    5  28419.745 ± 375.215  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls       Ktor_OkHttp  thrpt    5  15025.216 ± 486.722  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls            OkHttp  thrpt    5   7912.345 ± 352.180  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls               CRT  thrpt    5   8890.362 ± 711.467  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls       Ktor_OkHttp  thrpt    5   4410.282 ± 102.291  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls               OkHttp  thrpt    5     16.458 ±   0.531  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                  CRT  thrpt    5     17.432 ±   0.533  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls          Ktor_OkHttp  thrpt    5     16.343 ±   0.449  ops/s
```

The `Ktor_OkHttp` engine is the Ktor wrapped OkHttp engine whereas the `OkHttp` engine is the raw binding to OkHttp
without Ktor.