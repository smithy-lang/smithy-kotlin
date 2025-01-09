/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.*

/**
 * A (source, sink) connected pair. Writes to [sink] are read from [source]
 */
data class Pipe(val source: SdkBufferedSource, val sink: SdkBufferedSink)

internal fun bytes(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

fun interface BufferSourceFactory {
    fun pipe(): Pipe
}

object TestFactory {
    val BUFFER = BufferSourceFactory {
        val buffer = SdkBuffer()
        Pipe(buffer, buffer)
    }

    val BUFFERED_SOURCE = BufferSourceFactory {
        val buffer = SdkBuffer()
        Pipe((buffer as SdkSource).buffer(), buffer)
    }

    val PEEK_BUFFER = BufferSourceFactory {
        val buffer = SdkBuffer()
        Pipe(buffer.peek(), buffer)
    }

    val PEEK_BUFFERED_SOUCE = BufferSourceFactory {
        val buffer = SdkBuffer()
        Pipe((buffer as SdkSource).buffer().peek(), buffer)
    }
}

class SdkBufferSourceTest : BufferedSourceTest(TestFactory.BUFFER)
class SdkBufferedSourceTest : BufferedSourceTest(TestFactory.BUFFERED_SOURCE)
class PeekBufferSourceTest : BufferedSourceTest(TestFactory.PEEK_BUFFER)
class PeekBufferedSourceTest : BufferedSourceTest(TestFactory.PEEK_BUFFERED_SOUCE)

abstract class BufferedSourceTest(
    factory: BufferSourceFactory,
) {
    private val sink: SdkBufferedSink
    private val source: SdkBufferedSource
    init {
        val pipe = factory.pipe()
        sink = pipe.sink
        source = pipe.source
    }

    @Test
    fun testReadBytes() {
        sink.write(bytes(0xde, 0xad, 0xbe, 0xef))
        sink.flush()
        assertEquals(0xde.toByte(), source.readByte())
        assertEquals(0xad.toByte(), source.readByte())
        assertFalse(source.exhausted())
        assertEquals(0xbe.toByte(), source.readByte())
        assertEquals(0xef.toByte(), source.readByte())
        assertTrue(source.exhausted())
    }

    @Test
    fun testReadEmpty() {
        assertFailsWith<EOFException> {
            source.readByte()
        }
    }

    @Test
    fun testReadShort() {
        sink.write(bytes(0xde, 0xad, 0xbe, 0xef))
        sink.flush()
        assertEquals(0xdead.toShort(), source.readShort())
        assertEquals(0xbeef.toShort(), source.readShort())
    }

    @Test
    fun testReadShortLe() {
        sink.write(bytes(0xde, 0xad, 0xbe, 0xef))
        sink.flush()
        assertEquals(0xadde.toShort(), source.readShortLe())
        assertEquals(0xefbe.toShort(), source.readShortLe())
    }

    @Test
    fun testReadInt() {
        sink.write(bytes(0x0b, 0xad, 0xca, 0xfe))
        sink.flush()
        assertEquals(0x0badcafe, source.readInt())
    }

    @Test
    fun testReadIntLe() {
        sink.write(bytes(0x0b, 0xad, 0xca, 0xfe))
        sink.flush()
        assertEquals(-20271861, source.readIntLe())
    }

    @Test
    fun testReadLong() {
        sink.write(bytes(0xde, 0xad, 0xbe, 0xef, 0x10, 0x20, 0x30, 0x40))
        sink.flush()
        assertEquals(-2401053092341600192, source.readLong())
    }

    @Test
    fun testReadLongLe() {
        sink.write(bytes(0xde, 0xad, 0xbe, 0xef, 0x10, 0x20, 0x30, 0x40))
        sink.flush()
        assertEquals(4625232074423315934, source.readLongLe())
    }

    @Test
    fun testReadAll() {
        val content = "a lep is a ball"
        sink.writeUtf8(content)
        sink.flush()
        val testSink = SdkBuffer()
        assertEquals(content.length.toLong(), source.readAll(testSink))
        assertEquals(content.length.toLong(), testSink.size)
        assertEquals(content, testSink.readUtf8())
    }

    @Test
    fun testReadAllExhaustedSource() {
        val testSink: SdkSink = SdkBuffer()
        assertEquals(0, source.readAll(testSink))
    }

    @Test
    fun testReadExhausted() {
        val testSink = SdkBuffer()
        testSink.writeUtf8("a lep is a ball")
        val sizeBefore = testSink.size
        assertEquals(-1, source.read(testSink, 10))
        assertEquals(sizeBefore, testSink.size)
    }

    @Test
    fun testReadByteArray() {
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected)
        val actual = source.readByteArray()
        assertContentEquals(expected, actual)
    }

    @Test
    fun testReadByteArrayLimit() {
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected, 0, 2)
        val actual = source.readByteArray()
        assertContentEquals(expected.sliceArray(0..1), actual)
    }

    @Test
    fun testReadByteArrayOffset() {
        val content = bytes(0xde, 0xad, 0xbe, 0xef)
        val actual = ByteArray(8)
        sink.write(content)
        val rc = source.read(actual, 6)
        assertEquals(2, rc)
        val expected = bytes(0, 0, 0, 0, 0, 0, 0xde, 0xad)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testReadByteArrayOffsetAndLimit() {
        val content = bytes(0xde, 0xad, 0xbe, 0xef)
        val actual = ByteArray(8)
        sink.write(content)
        val rc = source.read(actual, 2, 4)
        assertEquals(4, rc)
        val expected = bytes(0, 0, 0xde, 0xad, 0xbe, 0xef, 0, 0)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testReadByteArrayTooSmall() {
        // read into a byte array that is smaller than the available data which should result in a "short" read
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected)
        val testSink = ByteArray(3)
        source.read(testSink)
        assertContentEquals(expected.sliceArray(0..2), testSink)
    }

    @Test
    fun testReadByteArrayEOF() {
        // read into a byte array that is smaller than the available data which should result in a "short" read
        sink.writeUtf8("12345")
        assertFailsWith<EOFException> {
            source.readByteArray(10)
        }
    }

    @Test
    fun testSkip() {
        val content = ByteArray(16 * 1024) { it.toByte() }
        sink.write(content)
        assertEquals(content.size.toLong(), sink.buffer.size)

        source.skip(8192)
        val actual = source.readByteArray()
        assertContentEquals(content.sliceArray(8192 until content.size), actual)
    }

    @Test
    fun testSkipNotEnoughData() {
        val content = ByteArray(1024) { it.toByte() }
        sink.write(content)
        assertEquals(content.size.toLong(), sink.buffer.size)

        assertFailsWith<EOFException> {
            source.skip(8192)
        }
    }

    @Test
    fun testPeek() {
        sink.writeUtf8("a flix is a comb")
        sink.flush()

        assertEquals("a flix", source.readUtf8(6))
        val peek = source.peek()
        assertEquals(" is a ", peek.readUtf8(6))
        assertEquals("comb", peek.readUtf8(4))

        assertEquals(" is a comb", source.readUtf8(10))
    }

    @Test
    fun testMultiplePeek() {
        sink.writeUtf8("a flix is a comb")
        sink.flush()

        assertEquals("a flix", source.readUtf8(6))
        val peek1 = source.peek()
        val peek2 = source.peek()
        assertEquals(" is a ", peek1.readUtf8(6))
        assertEquals(" is a ", peek2.readUtf8(6))

        assertEquals("comb", peek1.readUtf8(4))
        assertEquals("comb", peek2.readUtf8(4))

        assertEquals(" is a comb", source.readUtf8(10))
    }

    @Test
    fun testLargePeek() {
        sink.writeUtf8("123456")
        sink.writeUtf8("7".repeat(1024 * 16))
        sink.writeUtf8("89")
        sink.flush()

        assertEquals("123", source.readUtf8(3))
        val peek = source.peek()

        assertEquals("456", peek.readUtf8(3))
        peek.skip(1024 * 16)
        assertEquals("89", peek.readUtf8(2))

        assertEquals("456", source.readUtf8(3))
        source.skip(1024 * 16)
        assertEquals("89", source.readUtf8(2))
        assertTrue(source.exhausted())
    }

    @Test
    fun testInvalidatedPeek() {
        // peek is invalid after first call to source
        sink.writeUtf8("123456789")
        sink.flush()

        assertEquals("123", source.readUtf8(3))
        val peek = source.peek()
        assertEquals("456", peek.readUtf8(3))
        assertEquals("789", peek.readUtf8(3))

        assertEquals("456", source.readUtf8(3))

        assertFailsWith<IllegalStateException> {
            peek.readUtf8(3)
        }
    }

    @Test
    fun testRequest() {
        sink.writeUtf8("123456789".repeat(1024))
        sink.flush()

        assertTrue(source.request(8192))
        assertTrue(source.request(1024 * 9))
        assertFalse(source.request(1024 * 9 + 1))
    }

    @Test
    fun testRequire() {
        sink.writeUtf8("123456789".repeat(1024))
        sink.flush()

        source.require(1024 * 9)
        assertFailsWith<EOFException> {
            source.require(1024 * 9 + 1)
        }
    }

    @Test
    fun testReadFully() {
        val data = "123456789".repeat(1024)
        sink.writeUtf8(data)
        sink.flush()

        val dest = SdkBuffer()
        source.readFully(dest, data.length.toLong())
        assertEquals(data, dest.readUtf8())
    }

    @Test
    fun testReadFullyIllegalArgumentException() {
        val dest = SdkBuffer()
        assertFailsWith<IllegalArgumentException> {
            source.readFully(dest, -1)
        }
    }

    @Test
    fun testReadFullyEOFException() {
        val data = "123456789".repeat(1024)
        sink.writeUtf8(data)
        sink.flush()

        val dest = SdkBuffer()
        assertFailsWith<EOFException> {
            source.readFully(dest, data.length.toLong() + 1)
        }
    }
}
