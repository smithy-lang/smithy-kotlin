/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import java.util.zip.CRC32

actual class Crc32 : Crc32Base() {
    private val md = CRC32()

    override fun update(input: ByteArray, offset: Int, length: Int) = md.update(input, offset, length)

    override fun digest(): ByteArray {
        val x = digestValue()
        reset()
        return byteArrayOf(
            ((x shl 24) and 0xffu).toByte(),
            ((x shl 16) and 0xffu).toByte(),
            ((x shl 8) and 0xffu).toByte(),
            (x and 0xffu).toByte()
        )
    }

    override fun digestValue(): UInt = md.value.toUInt()

    override fun reset() = md.reset()
}
