/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.hashing

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.ecdsaSecp256r1
import aws.smithy.kotlin.runtime.hashing.pure.pureCrc32
import aws.smithy.kotlin.runtime.hashing.pure.pureCrc32c
import aws.smithy.kotlin.runtime.hashing.pure.pureEcdsaSecp256r1
import aws.smithy.kotlin.runtime.hashing.pure.pureMd5
import aws.smithy.kotlin.runtime.hashing.pure.pureSha1
import aws.smithy.kotlin.runtime.hashing.pure.pureSha256
import aws.smithy.kotlin.runtime.hashing.toHashFunctionOrThrow

/** The implementations currently shipped in runtime-core (JVM stdlib, CRT, or Kotlin depending on algorithm/platform) */
internal const val RUNTIME = "runtime"

/** The pure-Kotlin implementations in runtime-core's `hashing.pure` package */
internal const val PURE = "pure"

internal fun hashFunction(implementation: String, algorithm: String): HashFunction = when (implementation) {
    RUNTIME -> algorithm.toHashFunctionOrThrow()
    PURE -> pureHashFunctionSupplier(algorithm)()
    else -> error("Unknown implementation $implementation")
}

/** Supplies the [PURE] hash for [algorithm] */
internal fun pureHashFunctionSupplier(algorithm: String): () -> HashFunction = when (algorithm) {
    "crc32" -> ::pureCrc32
    "crc32c" -> ::pureCrc32c
    "md5" -> ::pureMd5
    "sha1" -> ::pureSha1
    "sha256" -> ::pureSha256
    else -> error("Unknown algorithm $algorithm")
}

internal fun ecdsaSigner(implementation: String): (ByteArray, ByteArray) -> ByteArray = when (implementation) {
    RUNTIME -> ::ecdsaSecp256r1
    PURE -> ::pureEcdsaSecp256r1
    else -> error("Unknown implementation $implementation")
}
