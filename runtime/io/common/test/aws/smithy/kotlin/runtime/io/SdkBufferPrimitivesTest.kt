/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

class SdkBufferPrimitivesTest {
    @Test
    fun readWriteByte() {
        val buf = SdkBuffer(8)
        buf.writeByte(0xFF.toByte())
        assertEquals(0xFF.toByte(), buf.readByte())
    }

    @Test
    fun readWriteShort() {
        val buf = SdkBuffer(8)
        buf.writeShort(0xFFFF.toShort())
        assertEquals(0xFFFF.toShort(), buf.readShort())
    }

    @Test
    fun readWriteInt() {
        val buf = SdkBuffer(8)
        buf.writeInt(0x0EADBEEF)
        assertEquals(0x0EADBEEF, buf.readInt())
    }

    @Test
    fun readWriteLong() {
        val buf = SdkBuffer(8)
        buf.writeLong(0xDEADBEEF)
        assertEquals(0xDEADBEEF, buf.readLong())
    }

    @Test
    fun readWriteFloat() {
        val buf = SdkBuffer(8)
        buf.writeFloat(2.237f)
        assertEquals(2.237f, buf.readFloat())
    }

    @Test
    fun readWriteDouble() {
        val buf = SdkBuffer(8)
        buf.writeDouble(2.237)
        assertEquals(2.237, buf.readDouble())
    }
}
