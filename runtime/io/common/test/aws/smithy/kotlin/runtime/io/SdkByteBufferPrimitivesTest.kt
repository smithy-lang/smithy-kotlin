/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

class SdkByteBufferPrimitivesTest {
    @Test
    fun readWriteByte() {
        val buf = SdkByteBuffer(8u)
        buf.writeByte(0xFF.toByte())
        assertEquals(0xFF.toByte(), buf.readByte())
    }

    @Test
    fun readWriteShort() {
        val buf = SdkByteBuffer(8u)
        buf.writeShort(0xFFFF.toShort())
        assertEquals(0xFFFF.toShort(), buf.readShort())
    }

    @Test
    fun readWriteUShort() {
        val buf = SdkByteBuffer(8u)
        buf.writeUShort(0x0FFF.toUShort())
        assertEquals(0x0FFF.toUShort(), buf.readUShort())
    }

    @Test
    fun readWriteInt() {
        val buf = SdkByteBuffer(8u)
        buf.writeInt(0x0EADBEEF)
        assertEquals(0x0EADBEEF, buf.readInt())
    }

    @Test
    fun readWriteUInt() {
        val buf = SdkByteBuffer(8u)
        buf.writeUInt(0x0EADBEEFu)
        assertEquals(0x0EADBEEFu, buf.readUInt())
    }

    @Test
    fun readWriteLong() {
        val buf = SdkByteBuffer(8u)
        buf.writeLong(0xDEADBEEF)
        assertEquals(0xDEADBEEF, buf.readLong())
    }

    @Test
    fun readWriteULong() {
        val buf = SdkByteBuffer(8u)
        buf.writeULong(0xDEADBEEFu)
        assertEquals(0xDEADBEEFu, buf.readULong())
    }

    @Test
    fun readWriteFloat() {
        val buf = SdkByteBuffer(8u)
        buf.writeFloat(2.237f)
        assertEquals(2.237f, buf.readFloat())
    }

    @Test
    fun readWriteDouble() {
        val buf = SdkByteBuffer(8u)
        buf.writeDouble(2.237)
        assertEquals(2.237, buf.readDouble())
    }
}
