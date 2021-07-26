/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

import java.security.MessageDigest
import java.util.zip.CRC32

actual class Sha256 : HashFunction {
    private val md = MessageDigest.getInstance("SHA-256")
    override fun update(input: ByteArray) = md.update(input)
    override fun digest(): ByteArray = md.digest()
    override fun reset() = md.reset()
}

actual class Md5 : HashFunction {
    private val md = MessageDigest.getInstance("MD5")
    override fun update(input: ByteArray) = md.update(input)
    override fun digest(): ByteArray = md.digest()
    override fun reset() = md.reset()
}

actual class Crc32 : HashFunction {
    private val md = CRC32()

    actual val value: UInt
        get() = md.value.toUInt()

    override fun update(input: ByteArray) = md.update(input)
    override fun digest(): ByteArray {
        val x = value
        reset()
        return byteArrayOf(
            ((x shl 24) and 0xffu).toByte(),
            ((x shl 16) and 0xffu).toByte(),
            ((x shl 8) and 0xffu).toByte(),
            (x and 0xffu).toByte()
        )
    }
    override fun reset() = md.reset()
}
