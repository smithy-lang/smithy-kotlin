# HTTP Client Engine Benchmarks

This project contains benchmarks for the [HTTP engine implementations](../../../runtime/protocol/http-client-engines).

## Testing

```sh
./gradlew :tests:benchmarks:http-benchmarks:benchmark
```

Baseline `0.14.0-SNAPSHOT` on EC2 **[m5.4xlarge](https://aws.amazon.com/ec2/instance-types/m5/)** with **Corretto-11.0.15.9.1**:

The download/upload throughput benchmarks are an approximation of how much data in MB/s we are able to process.

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