/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.JobChannel
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
    fun testTryWriteWithinCapacity() = runTest {
        SdkByteChannel(true, maxBufferSize = 16).use { chan ->
            val source = SdkBuffer().apply { write(byteArrayOf(1, 2, 3, 4)) }
            val written = chan.tryWrite(source)
            assertEquals(4L, written)
            assertEquals(4L, chan.totalBytesWritten)
            assertEquals(4, chan.availableForRead)
            assertEquals(0L, source.size)
        }
    }

    @Test
    fun testTryWritePartialWhenBufferNearlyFull() = runTest {
        SdkByteChannel(true, maxBufferSize = 8).use { chan ->
            // fill 6 of 8 bytes
            chan.tryWrite(SdkBuffer().apply { write(ByteArray(6)) })
            assertEquals(2, chan.availableForWrite)

            // only 2 bytes fit without suspending; tryWrite writes what it can
            val source = SdkBuffer().apply { write(ByteArray(5)) }
            val written = chan.tryWrite(source)
            assertEquals(2L, written)
            assertEquals(0, chan.availableForWrite)
            assertEquals(3L, source.size) // remaining bytes left in source
        }
    }

    @Test
    fun testTryWriteReturnsZeroWhenFull() = runTest {
        SdkByteChannel(true, maxBufferSize = 4).use { chan ->
            chan.tryWrite(SdkBuffer().apply { write(ByteArray(4)) })
            assertEquals(0, chan.availableForWrite)

            val written = chan.tryWrite(SdkBuffer().apply { write(ByteArray(4)) })
            assertEquals(0L, written)
        }
    }

    @Test
    fun testTryWriteRoundTrip() = runTest {
        SdkByteChannel(true, maxBufferSize = 64).use { chan ->
            val data = "hello flow control".encodeToByteArray()
            val written = chan.tryWrite(SdkBuffer().apply { write(data) })
            assertEquals(data.size.toLong(), written)

            val sink = SdkBuffer()
            chan.read(sink, data.size.toLong())
            assertContentEquals(data, sink.readByteArray())
        }
    }

    @Test
    fun testTryWriteReadFreesCapacityForSubsequentWrite() = runTest {
        SdkByteChannel(true, maxBufferSize = 8).use { chan ->
            assertEquals(8L, chan.tryWrite(SdkBuffer().apply { write(ByteArray(8)) }))
            assertEquals(0, chan.availableForWrite)

            // draining bytes must free capacity so the next tryWrite succeeds (models flow-control window replenish)
            val sink = SdkBuffer()
            chan.read(sink, 5)
            assertEquals(5, chan.availableForWrite)
            assertEquals(5L, chan.tryWrite(SdkBuffer().apply { write(ByteArray(5)) }))
        }
    }

    @Test
    fun testTryWriteOnClosedChannelThrows() = runTest {
        val chan = SdkByteChannel(true, maxBufferSize = 8)
        chan.close()
        assertFailsWith<ClosedWriteChannelException> {
            chan.tryWrite(SdkBuffer().apply { write(byteArrayOf(1)) })
        }
    }

    @Test
    fun testTryWriteOnChannelClosedWithCauseThrowsThatCause() = runTest {
        // When a channel is closed with a cause, tryWrite rethrows that exact cause (not ClosedWriteChannelException).
        // The CRT response handler relies on this: its write-path catch classifies by channel state rather than
        // exception type precisely because the thrown type is arbitrary.
        val chan = SdkByteChannel(true, maxBufferSize = 8)
        val cause = RuntimeException("consumer went away")
        chan.close(cause)
        val thrown = assertFailsWith<RuntimeException> {
            chan.tryWrite(SdkBuffer().apply { write(byteArrayOf(1)) })
        }
        assertEquals(cause, thrown)
    }

    @Test
    fun testTryWriteZeroByteCountIsNoOp() = runTest {
        SdkByteChannel(true, maxBufferSize = 8).use { chan ->
            assertEquals(0L, chan.tryWrite(SdkBuffer(), 0))
            assertEquals(0L, chan.totalBytesWritten)
        }
    }

    @Test
    fun testTryWriteForwardsThroughDelegatingWrapper() = runTest {
        // tryWrite is an interface member, so a delegating wrapper (SdkByteChannel by delegate) forwards it to its
        // RealSdkByteChannel delegate rather than hitting the unsupported default.
        JobChannel().use { chan ->
            val written = chan.tryWrite(SdkBuffer().apply { write(byteArrayOf(1, 2, 3)) })
            assertEquals(3L, written)
            assertEquals(3, chan.availableForRead)
        }
    }

    @Test
    fun testTryWriteDefaultIsUnsupported() {
        // an implementation that does not override tryWrite falls back to the throwing default
        val chan = object : SdkByteWriteChannel {
            override val availableForWrite: Int = 0
            override val isClosedForWrite: Boolean = false
            override val closedCause: Throwable? = null
            override val totalBytesWritten: Long = 0
            override val autoFlush: Boolean = true
            override suspend fun write(source: SdkBuffer, byteCount: Long) {}
            override fun close(cause: Throwable?): Boolean = true
            override fun close() {}
            override fun flush() {}
        }
        assertFailsWith<UnsupportedOperationException> {
            chan.tryWrite(SdkBuffer().apply { write(byteArrayOf(1)) })
        }
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
    fun testReadFullyFromCanceledChannel() = runTest {
        // ensure that we attempt reading such that failures are propagate to caller
        val chan = SdkByteChannel(true)

        chan.cancel(TestException())
        val sink = SdkBuffer()
        assertFailsWith<TestException> {
            chan.readFully(sink, 1)
        }
    }

    @Test
    fun testReadRemainingFromCanceledChannel() = runTest {
        // ensure that we attempt reading such that failures are propagate to caller
        val chan = SdkByteChannel(true)

        chan.cancel(TestException())

        val sink = SdkBuffer()
        assertFailsWith<TestException> {
            chan.readRemaining(sink)
        }
    }
}
