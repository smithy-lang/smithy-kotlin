/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

object Digest {
    /**
     * Compute the SHA-256 hash for the specified [input]
     */
    fun sha256(input: ByteArray): ByteArray = hash(Sha256(), input)

    /**
     * Compute the MD5 hash for the specified [input]
     */
    fun md5(input: ByteArray): ByteArray = hash(MD5(), input)
}

private fun hash(fn: HashFunction, input: ByteArray): ByteArray = fn.apply { update(input) }.digest()
