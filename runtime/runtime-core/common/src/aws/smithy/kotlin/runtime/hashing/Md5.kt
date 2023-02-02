/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public abstract class Md5Base : HashFunction {
    override val blockSizeBytes: Int = 64
    override val digestSizeBytes: Int = 16
}

/**
 * Implementation of RFC1321 MD5 digest
 */
@InternalApi
public expect class Md5() : Md5Base

/**
 * Compute the MD5 hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.md5(): ByteArray = hash(Md5(), this)
