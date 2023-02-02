/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public abstract class Sha256Base : HashFunction {
    override val blockSizeBytes: Int = 64
    override val digestSizeBytes: Int = 32
}

/**
 * Implementation of NIST SHA-256 hash function. See: https://csrc.nist.gov/projects/hash-functions

 */
@InternalApi
public expect class Sha256() : Sha256Base

/**
 * Compute the SHA-256 hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.sha256(): ByteArray = hash(Sha256(), this)
