/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.JobChannel
import aws.smithy.kotlin.runtime.testing.RandomTempFile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.CancellationException
import kotlin.test.*

class SdkSourceExtensionsTest {
    @Test
    fun testReadToByteArray() = runTest {
        val file = RandomTempFile(32 * 1024)
        val source = file.source()
        val actual = source.readToByteArray()
        assertEquals(file.length(), actual.size.toLong())
        assertContentEquals(file.readBytes(), actual)
    }

    @Test
    fun testToByteReadChannel() = runTest {
        val file = RandomTempFile(1024)
        val ch = file.source().toSdkByteReadChannel()

        val buffer = ch.readToBuffer()
        val actual = buffer.readByteArray()
        assertEquals(file.length(), actual.size.toLong())
        assertContentEquals(file.readBytes(), actual)
        assertTrue(ch.isClosedForRead)
        assertTrue(ch.isClosedForWrite)
    }

    @Test
    fun testSourceToReadChannelJobCancellation() = runTest {
        // coroutine launched in background will attempt to read
        // we then will simulate a failure reading from the underlying source and ensure the channel/job are closed
        // properly
        val source = object : SdkSource {
            val bufferChannel = Channel<SdkBuffer>(1)
            override fun close() { }
            override fun read(sink: SdkBuffer, limit: Long): Long {
                val buffer = runBlocking {
                    bufferChannel.receive()
                }
                val rc = buffer.size
                sink.writeAll(buffer)
                return rc
            }
        }

        val ch = source.toSdkByteReadChannel()
        yield()
        assertFalse(ch.isClosedForRead)
        assertFalse(ch.isClosedForWrite)

        val jobCh = assertIs<JobChannel>(ch)
        val job = assertNotNull(jobCh.job)
        assertTrue(job.isActive)

        // force the source.read() call to fail
        val ex = CancellationException("test source cancellation")
        source.bufferChannel.cancel(ex)

        job.join()

        assertTrue(job.isCancelled)
        assertTrue(ch.isClosedForRead)
        assertTrue(ch.isClosedForWrite)

        // assert failed channel
        assertNotNull(ch.closedCause)
    }
}
