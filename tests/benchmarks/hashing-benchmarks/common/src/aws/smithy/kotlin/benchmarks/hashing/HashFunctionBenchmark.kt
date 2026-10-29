/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.hashing

import aws.smithy.kotlin.runtime.hashing.HashFunction
import kotlinx.benchmark.*
import kotlin.random.Random

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class HashFunctionBenchmark {
    @Param(RUNTIME, PURE)
    var implementation: String = ""

    @Param("crc32", "crc32c", "md5", "sha1", "sha256")
    var algorithm: String = ""

    // Small payloads expose per-call overhead; large payloads expose raw throughput
    @Param("64", "1024", "1048576")
    var payloadSizeBytes: Int = 0

    private lateinit var hashFunction: HashFunction
    private lateinit var payload: ByteArray

    @Setup
    fun setup() {
        hashFunction = hashFunction(implementation, algorithm)
        payload = Random(42).nextBytes(payloadSizeBytes)
    }

    @Benchmark
    fun updateAndDigest(): ByteArray {
        hashFunction.update(payload)
        return hashFunction.digest() // also resets the hash function
    }
}
