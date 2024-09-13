# HTTP Client Engine Benchmarks

This project contains benchmarks for the [HTTP engine implementations](../../../runtime/protocol/http-client-engines).

## Testing

```sh
./gradlew :tests:benchmarks:http-benchmarks:benchmark
```

## Results

All tests are run on EC2 m5.4xlarge unless specified otherwise.

The download/upload throughput benchmarks are an approximation of how much data in MB/s we are able to process.

### 1.3.9
- Added OkHttp4 engine

```
jvm summary:
Benchmark                                                 (httpClientName)   Mode  Cnt      Score      Error  Units
HttpEngineBenchmarks.downloadThroughputNoTls                        OkHttp  thrpt    5    745.301 ±   35.401  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                           CRT  thrpt    5    378.639 ±   31.692  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                       OkHttp4  thrpt    5    751.228 ±   20.876  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                       OkHttp  thrpt    5  22678.327 ±  358.711  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                          CRT  thrpt    5  19444.576 ± 1766.956  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                      OkHttp4  thrpt    5  23325.643 ±  212.193  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                       OkHttp  thrpt    5   6370.241 ±  851.269  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                          CRT  thrpt    5   6024.056 ±  829.415  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                      OkHttp4  thrpt    5   6510.030 ±  464.146  ops/s
HttpEngineBenchmarks.uploadThroughputChannelContentNoTls            OkHttp  thrpt    5    189.346 ±    5.934  ops/s
HttpEngineBenchmarks.uploadThroughputChannelContentNoTls               CRT  thrpt    5    116.265 ±    0.240  ops/s
HttpEngineBenchmarks.uploadThroughputChannelContentNoTls           OkHttp4  thrpt    5    189.269 ±    6.007  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                          OkHttp  thrpt    5    188.174 ±    1.866  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                             CRT  thrpt    5    197.143 ±    2.890  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                         OkHttp4  thrpt    5    189.736 ±    3.535  ops/s
HttpEngineBenchmarks.uploadThroughputSourceContentNoTls             OkHttp  thrpt    5    197.732 ±    4.069  ops/s
HttpEngineBenchmarks.uploadThroughputSourceContentNoTls                CRT  thrpt    5    198.890 ±    1.889  ops/s
HttpEngineBenchmarks.uploadThroughputSourceContentNoTls            OkHttp4  thrpt    5    195.378 ±    2.165  ops/s
```

### 0.14.0-SNAPSHOT

```
jvm summary:
Benchmark                                                 (httpClientName)   Mode  Cnt      Score     Error  Units
HttpEngineBenchmarks.downloadThroughputNoTls                        OkHttp  thrpt    5    613.108 ±  23.914  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                           CRT  thrpt    5    406.408 ±  13.481  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                       OkHttp  thrpt    5  36524.268 ± 186.314  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                          CRT  thrpt    5  19593.107 ± 561.276  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                       OkHttp  thrpt    5   8377.427 ± 533.683  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                          CRT  thrpt    5   7352.441 ± 661.062  ops/s
HttpEngineBenchmarks.uploadThroughputChannelContentNoTls            OkHttp  thrpt    5    202.921 ±   4.127  ops/s
HttpEngineBenchmarks.uploadThroughputChannelContentNoTls               CRT  thrpt    5    115.932 ±   0.241  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                          OkHttp  thrpt    5    196.034 ±   5.392  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                             CRT  thrpt    5    206.827 ±   3.650  ops/s
HttpEngineBenchmarks.uploadThroughputSourceContentNoTls             OkHttp  thrpt    5    216.396 ±   7.071  ops/s
HttpEngineBenchmarks.uploadThroughputSourceContentNoTls                CRT  thrpt    5    211.232 ±  17.247  ops/s
```