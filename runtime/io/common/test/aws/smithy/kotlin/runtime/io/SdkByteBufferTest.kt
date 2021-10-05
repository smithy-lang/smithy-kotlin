/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import io.kotest.matchers.string.shouldContain
import io.ktor.utils.io.core.*
import kotlin.test.*

class SdkByteBufferTest {
    @Test
    fun testCtor() {
        val buf = SdkByteBuffer(128u)
        assertEquals(128u, buf.capacity)
        assertEquals(128u, buf.writeRemaining)
        assertEquals(0u, buf.readPosition)
        assertEquals(0u, buf.writePosition)
        assertEquals(0u, buf.readRemaining)
    }

    @Test
    fun testDiscard() {
        val buf = SdkByteBuffer(128u)
        assertEquals(0u, buf.discard(12u))
        assertEquals(0u, buf.readPosition)

        buf.advance(30u)
        assertEquals(12u, buf.discard(12u))
        assertEquals(12u, buf.readPosition)
        assertEquals(18u, buf.readRemaining)
    }

    @Test
    fun testCommitWritten() {
        val buf = SdkByteBuffer(128u)
        buf.advance(30u)
        buf.discard(2u)
        assertEquals(2u, buf.readPosition)
        assertEquals(28u, buf.readRemaining)
        assertEquals(98u, buf.writeRemaining)
        assertEquals(30u, buf.writePosition)

        assertFailsWith<IllegalArgumentException>("Unable to write 212 bytes; only 98 write capacity left") {
            buf.advance(212u)
        }
    }

    @Test
    fun testRewind() {
        val buf = SdkByteBuffer(128u)
        buf.advance(30u)
        buf.discard(30u)
        assertEquals(0u, buf.readRemaining)
        assertEquals(30u, buf.readPosition)
        buf.rewind(10u)
        assertEquals(10u, buf.readRemaining)
        assertEquals(20u, buf.readPosition)

        // past the beginning
        buf.rewind(1024u)
        assertEquals(30u, buf.readRemaining)
        assertEquals(0u, buf.readPosition)

        // test full rewind (default)
        buf.reset()
        buf.advance(30u)
        buf.discard(20u)
        assertEquals(10u, buf.readRemaining)
        assertEquals(20u, buf.readPosition)
        buf.rewind()
        assertEquals(30u, buf.readRemaining)
        assertEquals(0u, buf.readPosition)
    }

    @Test
    fun testReset() {
        val buf = SdkByteBuffer(128u)
        buf.advance(30u)
        buf.discard(30u)
        assertEquals(0u, buf.readRemaining)
        assertEquals(30u, buf.readPosition)

        buf.reset()
        assertEquals(0u, buf.writePosition)
        assertEquals(0u, buf.readPosition)
        assertEquals(128u, buf.writeRemaining)
        assertEquals(0u, buf.readRemaining)
    }

    @Test
    fun testReadFullyNotEnoughRemaining() {
        val buf = SdkByteBuffer(16u)
        buf.advance(12u)
        val sink = ByteArray(32)
        assertFailsWith<IllegalArgumentException>("Not enough bytes to read a ByteArray of size 32") {
            buf.readFully(sink)
        }
    }

    @Test
    fun testReadFullyInvalidOffset() {
        val buf = SdkByteBuffer(16u)
        buf.advance(12u)
        val sink = ByteArray(32)
        assertFailsWith<IllegalArgumentException>("Invalid read offset, must be positive: -2") {
            buf.readFully(sink, offset = -2)
        }
    }

    @Test
    fun testReadFullyInvalidLengthAndOffset() {
        val buf = SdkByteBuffer(16u)
        buf.advance(12u)
        val sink = ByteArray(8)
        assertFailsWith<IllegalArgumentException>(
            "Invalid read: offset + length should be less than the destination size: 7 + 4 < 8"
        ) {
            buf.readFully(sink, offset = 7, length = 4)
        }
    }

    @Test
    fun testReadFully() {
        val buf = SdkByteBuffer(8u)
        val contents = "Mad dog"
        buf.write(contents)
        val sink = ByteArray(8)
        buf.readFully(sink, length = 7)
        assertEquals(0u, buf.readRemaining)
        assertEquals(7u, buf.readPosition)
        assertEquals(7u, buf.writePosition)
        assertEquals(1u, buf.writeRemaining)

        assertEquals(contents, sink.sliceArray(0..6).decodeToString())

        // write at an offset
        buf.reset()
        buf.write(contents)
        buf.readFully(sink, offset = 2, length = 5)
        assertEquals("Mad d", sink.sliceArray(2..6).decodeToString())
    }

    @Test
    fun testReadAvailable() {
        val buf = SdkByteBuffer(8u)
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
        val buf = SdkByteBuffer(128u)
        val contents = "is it morning or is it night, the software engineer doesn't know anymore"
        buf.writeFully(contents.encodeToByteArray())
        val sink = ByteArray(buf.readRemaining.toInt())
        buf.readFully(sink)
        assertEquals(contents, sink.decodeToString())
    }

    @Test
    fun testWriteFullyInsufficientSpace() {
        val buf = SdkByteBuffer(16u)
        val contents = "is it morning or is it night, the software engineer doesn't know anymore"
        assertEquals(16u, buf.capacity)
        buf.writeFully(contents.encodeToByteArray())
        // content is 72 bytes. next power of 2 is greater than exp growth of current buffer
        assertEquals(128u, buf.capacity)

        val buf2 = SdkByteBuffer(16u)
        assertEquals(16u, buf2.capacity)
        buf2.advance(12u)
        val smallContent = byteArrayOf(1, 2, 3, 4, 5)
        buf2.writeFully(smallContent)
        // doubling the current capacity is greater
        assertEquals(32u, buf2.capacity)
    }

    @Test
    fun testReserve() {
        val buf = SdkByteBuffer(8u)
        assertEquals(8u, buf.capacity)
        buf.reserve(5)
        assertEquals(8u, buf.capacity)
        buf.reserve(12)
        assertEquals(16u, buf.capacity)

        buf.reserve(72)
        assertEquals(128u, buf.capacity)
    }

    @Test
    fun testReserveExistingData() {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/147
        val buf = SdkByteBuffer(256u)
        buf.advance(138u)
        buf.reserve(444)
        assertEquals(1024u, buf.capacity)
    }

    @Test
    fun testWriteFullyPastDestSize() {
        val buf = SdkByteBuffer(16u)
        val contents = byteArrayOf(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException>(
            "Invalid write: offset + length should be less than the source size: 2 + 4 < 5"
        ) {
            buf.writeFully(contents, offset = 2, length = 4)
        }
    }

    @Test
    fun testReadWriteString() {
        val buf = SdkByteBuffer(128u)
        val contents = "foo bar baz"
        buf.write(contents)
        assertEquals(contents.length.toULong(), buf.readRemaining)
        val actual = buf.decodeToString()
        assertEquals(contents, actual)
    }

    @Test
    fun testReadAvailableSdkBuffer() {
        val buf = SdkByteBuffer(8u)
        val contents = "Mad dog"
        buf.writeFully(contents.encodeToByteArray())
        val sink = SdkByteBuffer(8u)
        val rc = buf.readAvailable(sink)
        assertEquals(7UL, rc)

        // nothing left to read
        assertNull(buf.readAvailable(sink))
    }

    @Test
    fun testReadFullySdkBuffer() {
        val buf = SdkByteBuffer(8u)
        val contents = "Mad dog"
        buf.write(contents)
        val sink = SdkByteBuffer(8u)
        buf.readFully(sink, length = 7u)
        assertEquals(0u, buf.readRemaining)
        assertEquals(7u, buf.readPosition)
        assertEquals(7u, buf.writePosition)
        assertEquals(1u, buf.writeRemaining)

        assertEquals(1u, sink.writeRemaining)
        assertEquals(7u, sink.writePosition)
        assertEquals(7u, sink.readRemaining)
        assertEquals(0u, sink.readPosition)
    }

    @Test
    fun testWriteFullySdkBuffer() {
        val src = SdkByteBuffer(16u)
        src.write("buffers are fun!")
        assertEquals(16u, src.readRemaining)
        assertEquals(16u, src.capacity)

        val dest = SdkByteBuffer(8u)
        assertEquals(8u, dest.capacity)

        dest.writeFully(src)
        assertEquals(0u, src.readRemaining)
        assertEquals(16u, dest.readRemaining)
        assertEquals(16u, dest.capacity)
    }

    @Test
    fun testBytes() {
        val buf = SdkByteBuffer(32u)
        buf.advance(16u)
        val bytes = buf.bytes()
        assertEquals(16, bytes.size)
    }

    @Test
    fun testReadOnly() {
        val buf = SdkByteBuffer(16u, readOnly = true)
        val data = "foo"

        assertFailsWith<ReadOnlyBufferException> {
            buf.write(data)
        }
        assertFailsWith<ReadOnlyBufferException> {
            buf.writeFully(data.encodeToByteArray())
        }
        assertFailsWith<ReadOnlyBufferException> {
            val src = SdkByteBuffer.of(data.encodeToByteArray())
            buf.writeFully(src)
        }

        assertTrue(buf.isReadOnly)
        val buf2 = SdkByteBuffer(16u).asReadOnly()
        assertTrue(buf2.isReadOnly)
    }

    @Test
    fun testOfByteArray() {
        val data = "hello world".toByteArray()
        val buf = SdkByteBuffer.of(data)
        assertEquals(data.size.toULong(), buf.capacity)

        // does not automatically make the contents readable
        assertEquals(0u, buf.readRemaining)
        buf.advance(data.size.toULong())
        assertEquals(data.size.toULong(), buf.readRemaining)

        buf.reset()
        buf.advance(6u)
        buf.write("tests")
        assertEquals("hello tests", buf.decodeToString())

        // original buffer should have been modified
        assertEquals("hello tests", data.decodeToString())

        val buf2 = SdkByteBuffer.of(data).apply { advance(data.size.toULong()) }
        assertEquals(data.size.toULong(), buf2.capacity)
        assertEquals(data.size.toULong(), buf.readRemaining)
        assertEquals(0u, buf.writeRemaining)

        val buf3 = SdkByteBuffer.of(data, offset = 2, length = 5).apply { advance(5u) }
        assertEquals(5u, buf3.capacity)
        assertEquals(5u, buf3.readRemaining)
        assertEquals(0u, buf.writeRemaining)
    }

    @Test
    fun testFixedSizeBuffer() {
        val data = "hello world".toByteArray()
        val buf = SdkByteBuffer.of(data)
        buf.write("goodbye ")
        assertFailsWith<FixedBufferSizeException> {
            buf.write("world")
        }.message.shouldContain("5 bytes; writeRemaining: 3")
    }
}
