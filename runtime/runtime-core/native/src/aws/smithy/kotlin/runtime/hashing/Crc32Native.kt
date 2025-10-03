/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import aws.sdk.kotlin.crt.util.hashing.Crc32 as CrtCrc32

/**
 * CRC-32 checksum. Note: [digest] will return the bytes (big endian) of the CRC32 integer value. Access [digestValue]
 * directly to avoid doing the integer conversion yourself.
 */
@InternalApi
public actual class Crc32 actual constructor() : Crc32Base() {
    private val delegate = CrtCrc32()

    actual override fun digestValue(): UInt {
        val bytes = delegate.digest()
        return ((bytes[0].toUInt() and 0xffu) shl 24) or
            ((bytes[1].toUInt() and 0xffu) shl 16) or
            ((bytes[2].toUInt() and 0xffu) shl 8) or
            (bytes[3].toUInt() and 0xffu)
    }

    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = delegate.update(input, offset, length)
    actual override fun reset(): Unit = delegate.reset()
}
