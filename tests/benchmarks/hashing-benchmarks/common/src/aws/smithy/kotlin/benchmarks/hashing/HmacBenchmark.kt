/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.hashing

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.hashing.pure.pureHmacMd5
import aws.smithy.kotlin.runtime.hashing.pure.pureHmacSha1
import aws.smithy.kotlin.runtime.hashing.pure.pureHmacSha256
import kotlinx.benchmark.*
import kotlin.random.Random

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class HmacBenchmark {
    // RUNTIME runs runtime-core's (pure Kotlin) `hmac` over runtime-core's hash function. PURE runs `pureHmac<Hash>`, which
    // also precomputes the key-pad states, over the pure hash functions.
    @Param(RUNTIME, PURE)
    var implementation: String = ""

    // CRCs are omitted: their 4-byte block size makes them meaningless as HMAC hash functions
    @Param("md5", "sha1", "sha256")
    var algorithm: String = ""

    @Param("64", "1024", "1048576")
    var payloadSizeBytes: Int = 0

    private lateinit var hashFunction: HashFunction
    private lateinit var pureMac: (ByteArray) -> ByteArray
    private lateinit var key: ByteArray
    private lateinit var payload: ByteArray

    @Setup
    fun setup() {
        val random = Random(42)
        key = random.nextBytes(32)
        payload = random.nextBytes(payloadSizeBytes)
        if (implementation == PURE) {
            pureMac = when (algorithm) {
                "md5" -> pureHmacMd5(key)
                "sha1" -> pureHmacSha1(key)
                "sha256" -> pureHmacSha256(key)
                else -> error("Unknown algorithm $algorithm")
            }
        } else {
            hashFunction = hashFunction(implementation, algorithm)
        }
    }

    @Benchmark
    fun hmac(): ByteArray = if (implementation == PURE) pureMac(payload) else hmac(key, payload, hashFunction)
}
