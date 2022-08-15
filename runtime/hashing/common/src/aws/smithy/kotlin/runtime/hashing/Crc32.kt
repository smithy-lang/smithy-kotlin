/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.InternalApi

@InternalApi
public abstract class Crc32Base : HashFunction {
    override val blockSizeBytes: Int = 4
    override val digestSizeBytes: Int = 4

    public abstract fun digestValue(): UInt
}

/**
 * CRC-32 checksum. Note: [digest] will return the bytes (big endian) of the CRC32 integer value. Access [digestValue]
 * directly to avoid doing the integer conversion yourself.
 */
@InternalApi
public expect class Crc32() : Crc32Base

/**
 * Compute the MD5 hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.crc32(): UInt = Crc32().apply { update(this@crc32) }.digestValue()
