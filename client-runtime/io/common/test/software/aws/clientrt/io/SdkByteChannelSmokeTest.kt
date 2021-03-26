/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.io

import io.kotest.matchers.string.shouldContain
import io.ktor.utils.io.core.*
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.*

open class SdkByteChannelSmokeTest {

    @Test
    fun testCreateAndClose() {
        val chan = SdkByteChannel(false)
        chan.close()
    }

    @Test
    fun testAutoFlush() = runSuspendTest {
        SdkByteChannel(false).use { chan ->
            assertFalse(chan.autoFlush)
            chan.writeByte(1)
            chan.writeByte(2)
            assertEquals(0, chan.availableForRead)
            assertEquals(2, chan.totalBytesWritten)
            chan.flush()
            assertEquals(2, chan.availableForRead)
        }

        SdkByteChannel(true).use { chan ->
            assertTrue(chan.autoFlush)
            chan.writeByte(1)
            chan.writeByte(2)
            assertEquals(2, chan.totalBytesWritten)
            assertEquals(2, chan.availableForRead)
        }
    }

    @Test
    fun testClose() = runSuspendTest {
        val chan = SdkByteChannel(false)
        chan.writeByte(1)
        chan.writeByte(2)
        chan.writeByte(3)
        chan.flush()

        assertEquals(3, chan.availableForRead)
        assertFalse(chan.isClosedForRead)
        assertFalse(chan.isClosedForWrite)

        assertEquals(1, chan.readByte())
        chan.close()

        assertEquals(3, chan.totalBytesWritten)
        assertEquals(2, chan.readByte())
        assertEquals(3, chan.readByte())
        assertEquals(0, chan.availableForRead)
        assertTrue(chan.isClosedForRead)

        try {
            chan.readByte()
            fail("reading on an empty closed channel should have thrown")
        } catch (expected: EOFException) {
        } catch (expected: NoSuchElementException) {
        }
    }

    @Test
    fun testReadAndWriteFully() = runSuspendTest {
        val src = byteArrayOf(1, 2, 3, 4, 5)
        val sink = ByteArray(5)
        val chan = SdkByteChannel(false)

        chan.writeFully(src)
        chan.flush()
        assertEquals(5, chan.availableForRead)
        chan.readFully(sink)
        assertTrue { sink.contentEquals(src) }

        // split full read
        chan.writeFully(src)
        chan.flush()
        val partial = ByteArray(4)
        chan.readFully(partial)
        assertEquals(1, chan.availableForRead)
        assertEquals(5, chan.readByte())
        chan.close()

        try {
            chan.readByte()
            fail("reading on an empty closed channel should have thrown")
        } catch (expected: EOFException) {
        } catch (expected: NoSuchElementException) {
        }
    }

    @Test
    fun testReadAndWritePartial(): Unit = runSuspendTest {
        val src = byteArrayOf(1, 2, 3, 4, 5)
        val chan = SdkByteChannel(false)
        chan.writeFully(src)
        chan.flush()

        val buf1 = ByteArray(3)
        val rc1 = chan.readAvailable(buf1)
        assertEquals(3, rc1)

        chan.close()
        // requested full read size is larger than what's left after close
        val buf2 = ByteArray(16)
        val ex = assertFails {
            chan.readFully(buf2)
        }
        ex.message.shouldContain("expected 14 more bytes")
    }

    @Test
    fun testWriteString() = runSuspendTest {
        val chan = SdkByteChannel(false)
        val content = "I meant what I said. And said what I meant. An elephant's faithful. One hundred percent!"
        chan.writeUtf8(content)
        chan.close()
        val actual = chan.readAll().decodeToString()
        assertEquals(content, actual)
    }

    @Test
    fun testReadChannelByteArrayCtor() = runSuspendTest {
        val src = byteArrayOf(1, 2, 3, 4, 5)
        val chan = SdkByteReadChannel(src)
        assertTrue(chan.isClosedForWrite)
        assertFalse(chan.isClosedForRead)

        assertEquals(5, chan.availableForRead)
        val sink = ByteArray(5)
        var rc = chan.readAvailable(sink, 0, 4)
        assertEquals(4, rc)

        assertEquals(1, chan.availableForRead)
        rc = chan.readAvailable(sink, 4)
        assertEquals(1, rc)

        assertTrue { sink.contentEquals(src) }
        assertTrue(chan.isClosedForRead)
    }

    @Test
    fun testCloseableUse() = runSuspendTest {
        val chan = SdkByteChannel(true)
        chan.writeFully(byteArrayOf(1, 2, 3, 4, 5))
        val rc = chan.use {
            assertFalse(it.isClosedForWrite)
            assertFalse(it.isClosedForRead)
            println(it.availableForRead)
            val sink = ByteArray(4)
            it.readAvailable(sink)
        }
        assertTrue(chan.isClosedForWrite)
        assertFalse(chan.isClosedForRead)
        assertEquals(4, rc)
        chan.readByte()
        // should only flip after all bytes read
        assertTrue(chan.isClosedForRead)
    }
}
