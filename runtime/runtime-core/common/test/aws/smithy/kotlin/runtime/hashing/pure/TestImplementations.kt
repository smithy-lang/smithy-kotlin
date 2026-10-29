/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.toHashFunctionOrThrow

/** runtime-core's default implementations (JVM stdlib, CRT, or Kotlin depending on algorithm/platform) */
internal const val RUNTIME = "runtime"

/** The pure-Kotlin implementations in this package */
internal const val PURE = "pure"

internal fun hashFunction(implementation: String, algorithm: String): HashFunction = when (implementation) {
    RUNTIME -> algorithm.toHashFunctionOrThrow()
    PURE -> pureMerkleDamgardHashSupplier(algorithm)()
    else -> error("Unknown implementation $implementation")
}

/** Supplies the [PURE] block hash for [algorithm], as needed by [PureHmac] */
internal fun pureMerkleDamgardHashSupplier(algorithm: String): () -> MerkleDamgardHash = when (algorithm) {
    "md5" -> ::PureMd5
    "sha1" -> ::PureSha1
    "sha256" -> ::PureSha256
    else -> error("Unknown algorithm $algorithm")
}

internal fun pureHmacFor(algorithm: String, key: ByteArray): PureHmac = PureHmac(key, pureMerkleDamgardHashSupplier(algorithm)())
