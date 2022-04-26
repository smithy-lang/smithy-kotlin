/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.InternalApi

@InternalApi
abstract class Sha1Base : HashFunction {
    override val blockSizeBytes: Int = 64
    override val digestSizeBytes: Int = 20
}

/**
 * Implementation of SHA-1 (Secure Hash Algorithm 1) hash function. See: https://csrc.nist.gov/projects/hash-functions
 */
@InternalApi
expect class Sha1() : Sha1Base

/**
 * Compute the SHA-1 hash of the current [ByteArray]
 */
@InternalApi
fun ByteArray.sha1(): ByteArray = hash(Sha1(), this)
