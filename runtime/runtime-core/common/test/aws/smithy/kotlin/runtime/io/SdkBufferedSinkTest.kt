/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Test SdkBuffer implementation of SdkBufferedSink interface
class SdkBufferSinkTest : AbstractBufferedSinkTest({ buffer -> buffer })

// Test SdkBufferedSink implementation of _some_ underlying SdkSink that has been buffered
class SdkBufferedSinkTest : AbstractBufferedSinkTest({ buffer -> (buffer as SdkSink).buffer() })

// NOTE: Because the implementation is just a facade for okio these tests are sanity tests and not really exhaustive
abstract class AbstractBufferedSinkTest(
    factory: (SdkBuffer) -> SdkBufferedSink,
) {
    // underlying data sink is writing to
    private val data = SdkBuffer()
    private val sink = factory(data)

    @Test
    fun testWriteByte() {
        sink.writeByte(0xDE.toByte())
        sink.writeByte(0xAD.toByte())
        sink.writeByte(0xBE.toByte())
        sink.writeByte(0xEF.toByte())
        sink.flush()
        assertEquals("[hex=deadbeef]", data.toString())
    }

    @Test
    fun testWriteShort() {
        sink.writeShort(0xdead.toShort())
        sink.writeShort(0xbeef.toShort())
        sink.flush()
        assertEquals("[hex=deadbeef]", data.toString())
    }

    @Test
    fun testWriteShortLe() {
        sink.writeShortLe(0xdead.toShort())
        sink.writeShortLe(0xbeef.toShort())
        sink.flush()
        assertEquals("[hex=addeefbe]", data.toString())
    }

    @Test
    fun testWriteInt() {
        sink.writeInt(0xdeadbeef.toInt())
        sink.flush()
        assertEquals("[hex=deadbeef]", data.toString())
    }

    @Test
    fun testWriteLe() {
        sink.writeIntLe(0xdeadbeef.toInt())
        sink.flush()
        assertEquals("[hex=efbeadde]", data.toString())
    }

    @Test
    fun testWriteLong() {
        sink.writeLong(-2401053092341600192)
        sink.flush()
        assertEquals("[hex=deadbeef10203040]", data.toString())
    }

    @Test
    fun testWriteLongLe() {
        sink.writeLongLe(4625232074423315934)
        sink.flush()
        assertEquals("[hex=deadbeef10203040]", data.toString())
    }

    @Test
    fun testWriteString() {
        sink.writeUtf8("レップはボールです")
        sink.flush()
        assertEquals("[text=レップはボールです]", data.toString())
    }

    @Test
    fun testWriteSubstring() {
        sink.writeUtf8("a lep is a ball", start = 2, endExclusive = 10)
        sink.flush()
        assertEquals("lep is a", data.readUtf8())
    }

    @Test
    fun testWriteAll() {
        val contents = "a tay is a hammer"
        val src = SdkBuffer().apply { writeUtf8(contents) }
        val rc = sink.writeAll(src)
        sink.flush()
        assertEquals("a tay is a hammer", data.readUtf8())
        assertEquals(contents.length.toLong(), rc)
    }

    @Test
    fun testReadSourceFully() {
        val source = object : SdkSource by SdkBuffer() {
            override fun read(sink: SdkBuffer, limit: Long): Long {
                sink.writeUtf8("1234")
                return 4
            }
        }

        sink.write(source, 8)
        sink.flush()
        assertEquals("12341234", data.readUtf8())
    }

    @Test
    fun testWriteEof() {
        val source: SdkSource = SdkBuffer().apply { writeUtf8("1234") }
        assertFailsWith<EOFException> { sink.write(source, 8) }
        sink.flush()
        assertEquals("1234", data.readUtf8())
    }

    @Test
    fun testWriteExhausted() {
        val source: SdkSource = SdkBuffer()
        assertEquals(0, sink.writeAll(source))
        assertEquals(0, data.size)
    }

    @Test
    fun testWriteExplicitZero() {
        val source = object : SdkSource by SdkBuffer() {
            override fun read(sink: SdkBuffer, limit: Long): Long = error("should not reach this")
        }

        sink.write(source, 0)
        assertEquals(0, data.size)
    }

    @Test
    fun testCloseFlushes() {
        sink.writeUtf8("a flix is a comb")
        sink.close()
        assertEquals("a flix is a comb", data.readUtf8())
    }

    @Test
    fun testWriteByteArray() {
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected)
        sink.flush()
        val actual = data.readByteArray()
        assertContentEquals(expected, actual)
    }

    @Test
    fun testWriteByteArrayOffset() {
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected, 2)
        sink.flush()
        val actual = data.readByteArray()
        assertContentEquals(expected.sliceArray(2..3), actual)
    }

    @Test
    fun testWriteByteArrayOffsetAndLimit() {
        val expected = bytes(0xde, 0xad, 0xbe, 0xef)
        sink.write(expected, 1, 2)
        sink.flush()
        val actual = data.readByteArray()
        assertContentEquals(expected.sliceArray(1..2), actual)
    }
}
