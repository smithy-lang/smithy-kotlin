/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * A cryptographic hash function (algorithm)
 */
@InternalApi
public interface HashFunction {
    /**
     * The size of the hashing block in bytes.
     */
    public val blockSizeBytes: Int

    /**
     * The size of the digest output in bytes.
     */
    public val digestSizeBytes: Int

    /**
     * Update the running hash with [input] bytes. This can be called multiple times.
     */
    public fun update(input: ByteArray, offset: Int = 0, length: Int = input.size - offset)

    /**
     * Finalize the hash computation and return the digest bytes. The hash function will be [reset] after the call is
     * made.
     */
    public fun digest(): ByteArray

    /**
     * Resets the digest to its initial state discarding any accumulated digest state.
     */
    public fun reset()
}

internal fun hash(fn: HashFunction, input: ByteArray): ByteArray = fn.apply { update(input) }.digest()

/**
 * Compute a hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.hash(fn: HashFunction): ByteArray = hash(fn, this)

/**
 * A function that returns a new instance of a [HashFunction].
 */
@InternalApi
public typealias HashSupplier = () -> HashFunction

/**
 * Compute a hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.hash(hashSupplier: HashSupplier): ByteArray = hash(hashSupplier(), this)

@InternalApi
/**
 * Return the [HashFunction] which is represented by this string, or null if none match.
 */
public fun String.toHashFunction(): HashFunction? = when (this.lowercase()) {
    "crc32" -> Crc32()
    "crc32c" -> Crc32c()
    "sha1" -> Sha1()
    "sha256" -> Sha256()
    "md5" -> Md5()
    else -> null
}
