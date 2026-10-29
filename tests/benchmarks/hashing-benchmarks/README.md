# Hashing Benchmarks

This project contains benchmarks for the [hashing implementations](../../../runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/hashing)
(CRC32, CRC32C, MD5, SHA-1, SHA-256, HMAC, and ECDSA secp256r1) on the JVM and on the host's native target.

## Testing

```sh
./gradlew :tests:benchmarks:hashing-benchmarks:benchmark
```

Run a single platform with `jvmBenchmark` or `<nativeTarget>Benchmark` (e.g. `linuxX64Benchmark`, `macosArm64Benchmark`).

## Test data

Each benchmark processes a random payload of 64 B, 1 KiB, and 1 MiB:

* `HashFunctionBenchmark` — a single `update` followed by `digest`
* `HmacBenchmark` — HMAC with a 32-byte key over MD5, SHA-1, and SHA-256
* `EcdsaBenchmark` — an ECDSA secp256r1 signature (DER) with a fixed private key

## Implementations

Every benchmark is parameterized by `implementation`:

* `runtime` — what runtime-core ships (JVM stdlib, CRT, or Kotlin depending on algorithm and platform)
* `pure` — the pure-Kotlin implementations in runtime-core's [`hashing.pure`](../../../runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/hashing/pure)
  package

For `pure`, HMAC precomputes the key pads once per key (`pureHmacMd5` / `pureHmacSha1` / `pureHmacSha256`), while
`runtime` calls `hmac()` per op.

The `pure` MD5 and SHA-1 block functions are platform-specific (`jvm/src` and `native/src` in runtime-core): native is
fastest with every round written out and the message words in locals, which the JVM's JIT handles worse than rolled
loops.

The `pure` ECDSA arithmetic is written to be constant-time with respect to the private key and nonce. The exceptions,
which leak only rare or public events, are listed on `pureEcdsaSecp256r1`.

## Timing leak tests

runtime-core's `DudectTimingTest` runs dudect-style tests: it times an operation on a fixed secret vs random secrets and
flags a leak when Welch's |t| exceeds 4.5. A variable-time double-and-add k * G is the positive control. The tests are
slow and sensitive to host load, so they only run with `-Pdudect`:

```sh
./gradlew -Pdudect :runtime:runtime-core:jvmTest
```

Native test binaries are debug builds by default, which don't reflect what LLVM does to optimized code. With `-Pdudect`
the debug native test tasks are disabled, and each native target gets an optimized test binary (`dudect.kexe`). The
host's own target also gets a task that runs only the timing tests in it:

```sh
./gradlew -Pdudect :runtime:runtime-core:dudectReleaseLinuxX64
```

The `Timing leak tests` GitHub workflow (`.github/workflows/dudect.yml`) runs the JVM, linuxX64 and macosArm64 timing
tests weekly and on changes to the pure hashing code. Keep it out of the required PR checks: shared runners are noisy.
