/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

/**
 * CRC-32 checksum. Note: [digest] will return the bytes (big endian) of the CRC32 integer value. Access [digestValue]
 * directly to avoid doing the integer conversion yourself.
 */
@InternalApi
public actual class Crc32 actual constructor() : Crc32Base() {
    override fun digestValue(): UInt {
        TODO("Not yet implemented")
    }

    override fun update(input: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}
