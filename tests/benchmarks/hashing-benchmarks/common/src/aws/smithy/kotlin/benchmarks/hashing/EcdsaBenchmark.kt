/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.hashing

import kotlinx.benchmark.*
import kotlin.random.Random

// P-256 private key from RFC 6979 appendix A.2.5
@OptIn(ExperimentalStdlibApi::class)
private val privateKey = "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray()

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class EcdsaBenchmark {
    @Param(RUNTIME, PURE)
    var implementation: String = ""

    @Param("64", "1024", "1048576")
    var payloadSizeBytes: Int = 0

    private lateinit var sign: (ByteArray, ByteArray) -> ByteArray
    private lateinit var payload: ByteArray

    @Setup
    fun setup() {
        sign = ecdsaSigner(implementation)
        payload = Random(42).nextBytes(payloadSizeBytes)
        // Warm-up call so one-time lazy state (e.g. the pure fixed-base table) is built outside the timed region
        sign(privateKey, payload)
    }

    @Benchmark
    fun ecdsaSecp256r1(): ByteArray = sign(privateKey, payload)
}
