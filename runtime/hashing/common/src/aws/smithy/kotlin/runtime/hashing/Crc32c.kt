/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.InternalApi

@InternalApi
public abstract class Crc32cBase : HashFunction {
    override val blockSizeBytes: Int = 4
    override val digestSizeBytes: Int = 4

    public abstract fun digestValue(): UInt
}

/**
 * CRC32C checksum. Note: [digest] will return the bytes (big endian) of the CRC32C integer value. Access [digestValue]
 * directly to avoid doing the integer conversion yourself.
 */
@InternalApi
public expect class Crc32c() : Crc32cBase

/**
 * Compute the CRC32C hash of the current [ByteArray]
 */
@InternalApi
public fun ByteArray.crc32c(): UInt = Crc32c().apply { update(this@crc32c) }.digestValue()
