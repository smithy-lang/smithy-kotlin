# Serde Benchmarks

This project contains benchmarks for the [AWS signer implementations](../../../runtime/auth).

## Testing

```sh
./gradlew :tests:benchmarks:aws-signing-benchmarks:benchmark
```

Baseline `0.9.0-beta` on EC2 **[m5.4xlarge](https://aws.amazon.com/ec2/instance-types/m5/)** in **OpenJK 1.8.0_312**:

```
jvm summary:
Benchmark                            (signerName)  Mode  Cnt   Score   Error  Units
AwsSignerBenchmark.signingBenchmark       default  avgt    5  33.592 ± 0.103  us/op
AwsSignerBenchmark.signingBenchmark           crt  avgt    5  38.919 ± 0.494  us/op
```

## Test data

The current test data consist of [a synthetic request with a 1KB payload][request-code].

[request-code]: jvm/src/aws/smithy/kotlin/benchmarks/auth/signing/AwsSignerBenchmark.kt#L25-L40
