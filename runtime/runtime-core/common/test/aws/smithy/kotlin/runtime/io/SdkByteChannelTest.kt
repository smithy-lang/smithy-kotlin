/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class SdkByteChannelTest {
    @Test
    fun testCreateAndClose() {
        val chan = SdkByteChannel(false)
        chan.close()
    }

    @Test
    fun testAutoFlush() = runTest {
        SdkByteChannel(false).use { chan ->
            assertFalse(chan.autoFlush)
            val source = SdkBuffer()
            source.write(byteArrayOf(1, 2))
            chan.write(source, 2)

            assertEquals(0, chan.availableForRead)
            assertEquals(2, chan.totalBytesWritten)
            chan.flush()
            assertEquals(2, chan.availableForRead)
        }

        SdkByteChannel(true).use { chan ->
            assertTrue(chan.autoFlush)
            val source = SdkBuffer()
            source.write(byteArrayOf(1, 2))
            chan.write(source, 2)
            assertEquals(2, chan.totalBytesWritten)
            assertEquals(2, chan.availableForRead)
        }
    }

    @Test
    fun testClose() = runTest {
        val chan = SdkByteChannel(false)
        val source = SdkBuffer()
        source.writeByte(1)
        chan.write(source)
        chan.flush()
        assertEquals(1, chan.availableForRead)

        source.writeByte(2)
        source.writeByte(3)
        chan.write(source)

        assertFalse(chan.isClosedForRead)
        assertFalse(chan.isClosedForWrite)

        val sink = SdkBuffer()
        assertEquals(1, chan.read(sink, 1))
        assertEquals(1, sink.readByte())
        chan.close()
        assertTrue(chan.isClosedForWrite)

        // should have flushed
        assertEquals(2, chan.availableForRead)

        assertEquals(3, chan.totalBytesWritten)
        assertEquals(2, chan.read(sink, 8))

        assertEquals(2, sink.readByte())
        assertEquals(3, sink.readByte())
        assertEquals(0, chan.availableForRead)
        assertTrue(chan.isClosedForRead)

        // read from closed channel
        assertEquals(-1, chan.read(sink, 1))

        source.writeByte(4)

        // write to closed channel
        assertFailsWith<ClosedWriteChannelException> {
            chan.write(source)
        }
    }

    @Test
    fun testReadFromClosedChannel() = runTest {
        val chan = SdkByteReadChannel(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(chan.isClosedForWrite)
        val buffer = SdkBuffer()
        var rc = chan.read(buffer, 3)
        assertEquals(3, rc)

        rc = chan.read(buffer, Long.MAX_VALUE)
        assertEquals(2, rc)
        assertTrue { chan.isClosedForRead }
    }

    @Test
    fun testReadAvailableNoSuspend() = runTest {
        val chan = SdkByteReadChannel("world!".encodeToByteArray())
        val buffer = SdkBuffer()
        buffer.writeUtf8("hello, ")

        val rc = chan.read(buffer, Long.MAX_VALUE)
        assertEquals(6, rc)

        assertEquals("hello, world!", buffer.readUtf8())
    }

    @Test
    fun testReadAvailableSuspend() = runTest {
        val chan = SdkByteChannel()
        val job = launch {
            val buffer = SdkBuffer()
            buffer.writeUtf8("hello, ")

            // should suspend
            val rc = chan.read(buffer, Long.MAX_VALUE)
            assertEquals(6, rc)

            assertEquals("hello, world!", buffer.readUtf8())
        }
        yield()

        // should resume
        val sink = SdkBuffer().apply { writeUtf8("world!") }
        chan.write(sink)

        job.join()
    }

    @Test
    fun testCloseableUse() = runTest {
        val chan = SdkByteChannel(true)
        val source = SdkBuffer().apply { write(byteArrayOf(1, 2, 3, 4, 5)) }
        val sink = SdkBuffer()
        chan.write(source)
        val rc = chan.use {
            assertFalse(it.isClosedForWrite)
            assertFalse(it.isClosedForRead)
            it.read(sink, 4)
        }
        assertTrue(chan.isClosedForWrite)
        assertFalse(chan.isClosedForRead)
        assertEquals(4, rc)

        assertEquals(1, chan.read(sink, Long.MAX_VALUE))

        // should only flip after all bytes read
        assertTrue(chan.isClosedForRead)
    }

    @Test
    fun testReadFullyFromFailedChannel() = runTest {
        // ensure that we attempt reading such that failures are propagate to caller
        val chan = SdkByteChannel(true)

        chan.cancel(TestException())
        val sink = SdkBuffer()
        assertFailsWith<TestException> {
            chan.readFully(sink, 1)
        }
    }

    @Test
    fun testReadRemainingFromFailedChannel() = runTest {
        // ensure that we attempt reading such that failures are propagate to caller
        val chan = SdkByteChannel(true)

        chan.cancel(TestException())

        val sink = SdkBuffer()
        assertFailsWith<TestException> {
            chan.readRemaining(sink)
        }
    }
}
