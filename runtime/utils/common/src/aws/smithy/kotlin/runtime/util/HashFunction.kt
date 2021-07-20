/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

/**
 * A cryptographic hash function (algorithm)
 */
interface HashFunction {
    /**
     * Add [input] to the digest value
     */
    fun update(input: ByteArray)

    /**
     * Calculate the digest bytes
     */
    fun digest(): ByteArray

    /**
     * Resets the digest to it's initial state discarding any
     * accumulated digest state.
     */
    fun reset()
}

private fun hash(fn: HashFunction, input: ByteArray): ByteArray = fn.apply { update(input) }.digest()

/**
 * Implementation of NIST SHA-256 hash function
 * https://csrc.nist.gov/projects/hash-functions
 */
expect class Sha256() : HashFunction

/**
 * Implementation of RFC1321 MD5 digest
 */
expect class Md5() : HashFunction

/**
 * Compute the SHA-256 hash of the current [ByteArray]
 */
fun ByteArray.sha256(): ByteArray = hash(Sha256(), this)

/**
 * Compute the MD5 hash of the current [ByteArray]
 */
fun ByteArray.md5(): ByteArray = hash(Md5(), this)
