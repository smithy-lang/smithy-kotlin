# HTTP Client Engine Benchmarks

This project contains benchmarks for the [HTTP engine implementations](../../../runtime/protocol/http-client-engines).

## Testing

```sh
./gradlew :tests:benchmarks:http-benchmarks:benchmark
```

Baseline `0.13.0-SNAPSHOT` on EC2 **[m5.4xlarge](https://aws.amazon.com/ec2/instance-types/m5/)** with **Corretto-11.0.15.9.1**:

The download/upload throughput benchmarks are an approximation of how much data in MB/s we are able to process.

```
jvm summary:
Benchmark                                            (httpClientName)   Mode  Cnt      Score     Error  Units
HttpEngineBenchmarks.downloadThroughputNoTls                   OkHttp  thrpt    5    467.323 ±  13.279  ops/s
HttpEngineBenchmarks.downloadThroughputNoTls                      CRT  thrpt    5    415.115 ±   7.405  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                  OkHttp  thrpt    5  36166.495 ± 312.450  ops/s
HttpEngineBenchmarks.roundTripConcurrentNoTls                     CRT  thrpt    5  20070.294 ± 455.729  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                  OkHttp  thrpt    5   8308.011 ± 305.540  ops/s
HttpEngineBenchmarks.roundTripSequentialNoTls                     CRT  thrpt    5   7710.832 ± 579.525  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                     OkHttp  thrpt    5    206.267 ±   6.569  ops/s
HttpEngineBenchmarks.uploadThroughputNoTls                        CRT  thrpt    5    216.031 ±   3.804  ops/s
HttpEngineBenchmarks.uploadThroughputStreamingNoTls            OkHttp  thrpt    5    208.176 ±   6.893  ops/s
HttpEngineBenchmarks.uploadThroughputStreamingNoTls               CRT  thrpt    5    115.984 ±   0.265  ops/s
```

