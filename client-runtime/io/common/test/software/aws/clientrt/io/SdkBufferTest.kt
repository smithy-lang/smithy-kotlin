/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SdkBufferTest {
    @Test
    fun testCtor() {
        val buf = SdkBuffer(128)
        assertEquals(128, buf.capacity)
        assertEquals(128, buf.writeRemaining)
        assertEquals(0, buf.readPosition)
        assertEquals(0, buf.writePosition)
        assertEquals(0, buf.readRemaining)
    }

    @Test
    fun testDiscard() {
        val buf = SdkBuffer(128)
        assertFailsWith<IllegalArgumentException>("cannot discard -12 bytes; amount must be positive") {
            buf.discard(-12)
        }

        assertEquals(0, buf.discard(12))
        assertEquals(0, buf.readPosition)

        buf.commitWritten(30)
        assertEquals(12, buf.discard(12))
        assertEquals(12, buf.readPosition)
        assertEquals(18, buf.readRemaining)
    }

    @Test
    fun testCommitWritten() {
        val buf = SdkBuffer(128)
        buf.commitWritten(30)
        buf.discard(2)
        assertEquals(2, buf.readPosition)
        assertEquals(28, buf.readRemaining)
        assertEquals(98, buf.writeRemaining)
        assertEquals(30, buf.writePosition)

        assertFailsWith<IllegalArgumentException>("Unable to write 212 bytes; only 98 write capacity left") {
            buf.commitWritten(212)
        }
    }

    @Test
    fun testRewind() {
        val buf = SdkBuffer(128)
        buf.commitWritten(30)
        buf.discard(30)
        assertEquals(0, buf.readRemaining)
        assertEquals(30, buf.readPosition)
        buf.rewind(10)
        assertEquals(10, buf.readRemaining)
        assertEquals(20, buf.readPosition)

        // past the beginning
        buf.rewind(1024)
        assertEquals(30, buf.readRemaining)
        assertEquals(0, buf.readPosition)

        // test full rewind (default)
        buf.reset()
        buf.commitWritten(30)
        buf.discard(20)
        assertEquals(10, buf.readRemaining)
        assertEquals(20, buf.readPosition)
        buf.rewind()
        assertEquals(30, buf.readRemaining)
        assertEquals(0, buf.readPosition)
    }

    @Test
    fun testReset() {
        val buf = SdkBuffer(128)
        buf.commitWritten(30)
        buf.discard(30)
        assertEquals(0, buf.readRemaining)
        assertEquals(30, buf.readPosition)

        buf.reset()
        assertEquals(0, buf.writePosition)
        assertEquals(0, buf.readPosition)
        assertEquals(128, buf.writeRemaining)
        assertEquals(0, buf.readRemaining)
    }

    @Test
    fun testReadFullyNotEnoughRemaining() {
        val buf = SdkBuffer(16)
        buf.commitWritten(12)
        val sink = ByteArray(32)
        assertFailsWith<IllegalArgumentException>("Not enough bytes to read a ByteArray of size 32") {
            buf.readFully(sink)
        }
    }

    @Test
    fun testReadFullyInvalidOffset() {
        val buf = SdkBuffer(16)
        buf.commitWritten(12)
        val sink = ByteArray(32)
        assertFailsWith<IllegalArgumentException>("Invalid read offset, must be positive: -2") {
            buf.readFully(sink, offset = -2)
        }
    }

    @Test
    fun testReadFullyInvalidLengthAndOffset() {
        val buf = SdkBuffer(16)
        buf.commitWritten(12)
        val sink = ByteArray(8)
        assertFailsWith<IllegalArgumentException>(
            "Invalid read: offset + length should be less than the destination size: 7 + 4 < 8"
        ) {
            buf.readFully(sink, offset = 7, length = 4)
        }
    }

    @Test
    fun testReadFully() {
        val buf = SdkBuffer(8)
        val contents = "Mad dog"
        buf.write(contents)
        val sink = ByteArray(8)
        buf.readFully(sink, length = 7)
        assertEquals(0, buf.readRemaining)
        assertEquals(7, buf.readPosition)
        assertEquals(7, buf.writePosition)
        assertEquals(1, buf.writeRemaining)

        assertEquals(contents, sink.sliceArray(0..6).decodeToString())

        // write at an offset
        buf.reset()
        buf.write(contents)
        buf.readFully(sink, offset = 2, length = 5)
        assertEquals("Mad d", sink.sliceArray(2..6).decodeToString())
    }

    @Test
    fun testReadAvailable() {
        val buf = SdkBuffer(8)
        val contents = "Mad dog"
        buf.writeFully(contents.encodeToByteArray())
        val sink = ByteArray(8)
        val rc = buf.readAvailable(sink)
        assertEquals(7, rc)

        // nothing left to read
        assertEquals(-1, buf.readAvailable(sink))

        buf.reset()
        buf.write(contents)
        val sink2 = ByteArray(16)
        assertEquals(7, buf.readAvailable(sink2, offset = 2, length = 12))
        assertEquals(contents, sink2.sliceArray(2..8).decodeToString())
    }

    @Test
    fun testWriteFully() {
        val buf = SdkBuffer(128)
        val contents = "is it morning or is it night, the software engineer doesn't know anymore"
        buf.writeFully(contents.encodeToByteArray())
        val sink = ByteArray(buf.readRemaining)
        buf.readFully(sink)
        assertEquals(contents, sink.decodeToString())
    }

    @Test
    fun testWriteFullyInsufficientSpace() {
        val buf = SdkBuffer(16)
        val contents = "is it morning or is it night, the software engineer doesn't know anymore"
        assertFailsWith<IllegalArgumentException> (
            "Insufficient space to write ${contents.length} bytes; capacity available: 16"
        ) {
            buf.writeFully(contents.encodeToByteArray())
        }
    }

    @Test
    fun testWriteFullyPastDestSize() {
        val buf = SdkBuffer(16)
        val contents = byteArrayOf(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException>(
            "Invalid write: offset + length should be less than the source size: 2 + 4 < 5"
        ) {
            buf.writeFully(contents, offset = 2, length = 4)
        }
    }

    @Test
    fun testReadWriteString() {
        val buf = SdkBuffer(128)
        val contents = "foo bar baz"
        buf.write(contents)
        assertEquals(contents.length, buf.readRemaining)
        val actual = buf.decodeToString()
        assertEquals(contents, actual)
    }

    @Test
    fun testReadAvailableSdkBuffer() {
        val buf = SdkBuffer(8)
        val contents = "Mad dog"
        buf.writeFully(contents.encodeToByteArray())
        val sink = SdkBuffer(8)
        val rc = buf.readAvailable(sink)
        assertEquals(7, rc)

        // nothing left to read
        assertEquals(-1, buf.readAvailable(sink))
    }

    @Test
    fun testReadFullySdkBuffer() {
        val buf = SdkBuffer(8)
        val contents = "Mad dog"
        buf.write(contents)
        val sink = SdkBuffer(8)
        buf.readFully(sink, length = 7)
        assertEquals(0, buf.readRemaining)
        assertEquals(7, buf.readPosition)
        assertEquals(7, buf.writePosition)
        assertEquals(1, buf.writeRemaining)

        assertEquals(1, sink.writeRemaining)
        assertEquals(7, sink.writePosition)
        assertEquals(7, sink.readRemaining)
        assertEquals(0, sink.readPosition)
    }
}
