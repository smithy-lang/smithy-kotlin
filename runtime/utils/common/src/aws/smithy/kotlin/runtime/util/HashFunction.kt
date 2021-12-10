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
     * Update the running hash with [input] bytes. This can be called multiple times.
     */
    fun update(input: ByteArray)

    /**
     * Finalize the hash computation and return the digest bytes.
     * The hash function will be [reset] after the call is made.
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
 * Implementation of SHA-1 (Secure Hash Algorithm 1) hash function
 * https://csrc.nist.gov/projects/hash-functions
 */
expect class Sha1() : HashFunction

/**
 * Compute the SHA-256 hash of the current [ByteArray]
 */
fun ByteArray.sha256(): ByteArray = hash(Sha256(), this)

/**
 * Compute the SHA-1 hash of the current [ByteArray]
 */
fun ByteArray.sha1(): ByteArray = hash(Sha1(), this)

/**
 * Compute the MD5 hash of the current [ByteArray]
 */
fun ByteArray.md5(): ByteArray = hash(Md5(), this)
