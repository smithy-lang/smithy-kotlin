/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.experimental.xor

/**
 * Calculates the HMAC of a key and message using the given hashing algorithm.
 * @param key The cryptographic key to use for the HMAC. This key will be truncated and/or padded as necessary to fit
 * the block size of [hashFunction].
 * @param message The message to HMAC.
 * @param hashFunction The hashing algorithm to use.
 */
@InternalApi
fun hmac(key: ByteArray, message: ByteArray, hashFunction: HashFunction): ByteArray {
    val blockSizedKey = key.resizeToBlock(hashFunction)

    val innerKey = blockSizedKey xor 0x36
    val outerKey = blockSizedKey xor 0x5c

    hashFunction.update(innerKey)
    hashFunction.update(message)
    val innerHash = hashFunction.digest()

    hashFunction.update(outerKey)
    hashFunction.update(innerHash)
    return hashFunction.digest()
}

/**
 * Calculates the HMAC of a key and message using the given hashing algorithm.
 * @param key The cryptographic key to use for the HMAC. This key will be truncated and/or padded as necessary to fit
 * the block size of the hash function provided by [hashSupplier].
 * @param message The message to HMAC.
 * @param hashSupplier A supplier that yields a hashing algorithm to use.
 */
@InternalApi
fun hmac(key: ByteArray, message: ByteArray, hashSupplier: HashSupplier): ByteArray =
    hmac(key, message, hashSupplier())

private fun ByteArray.resizeToBlock(hashFunction: HashFunction): ByteArray {
    val blockSize = hashFunction.blockSizeBytes
    val truncated = if (size > blockSize) hash(hashFunction) else this
    return if (truncated.size < blockSize) truncated.copyOf(blockSize) else truncated
}

private infix fun ByteArray.xor(byte: Byte) = ByteArray(size) { this[it] xor byte }
