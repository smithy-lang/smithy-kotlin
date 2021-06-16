/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.*

class SdkByteChannelOpsTest {

    @Test
    fun testCopyTo() = runSuspendTest {
        val dst = SdkByteChannel(false)

        val contents = byteArrayOf(1, 2, 3, 4, 5)
        val src1 = SdkByteReadChannel(contents)
        val copied = src1.copyTo(dst, close = false)
        assertEquals(5, copied)

        val buffer = ByteArray(5)
        dst.readAvailable(buffer)
        assertTrue { contents.contentEquals(buffer) }
        assertFalse(dst.isClosedForWrite)

        val src2 = SdkByteReadChannel(contents)
        val rc = src2.copyTo(dst, limit = 3)
        assertTrue(dst.isClosedForWrite)
        assertEquals(3, rc)
        dst.readAvailable(buffer)
        val expected = byteArrayOf(1, 2, 3)
        assertTrue { expected.contentEquals(buffer.sliceArray(0..2)) }
    }

    @Test
    fun testCopyToFallback() = runSuspendTest {
        val dst = SdkByteChannel(false)

        val contents = byteArrayOf(1, 2, 3, 4, 5)
        val src1 = SdkByteReadChannel(contents)
        val copied = src1.copyToFallback(dst, Long.MAX_VALUE)
        assertEquals(5, copied)

        val buffer = ByteArray(5)
        dst.readAvailable(buffer)
        assertTrue { contents.contentEquals(buffer) }
        assertFalse(dst.isClosedForWrite)

        val src2 = SdkByteReadChannel(contents)
        val rc = src2.copyToFallback(dst, limit = 3)
        dst.close()
        assertTrue(dst.isClosedForWrite)
        assertEquals(3, rc)
        dst.readAvailable(buffer)
        val expected = byteArrayOf(1, 2, 3)
        assertTrue { expected.contentEquals(buffer.sliceArray(0..2)) }
    }

    @Test
    fun testCopyToSameOrZero() = runSuspendTest {
        val chan = SdkByteChannel(false)
        assertFailsWith<IllegalArgumentException> {
            chan.copyTo(chan)
        }
        val dst = SdkByteChannel(false)
        assertEquals(0, chan.copyTo(dst, limit = 0))
    }

    @Test
    fun testReadFromClosedChannel() = runSuspendTest {
        val chan = SdkByteReadChannel(byteArrayOf(1, 2, 3, 4, 5))
        val buffer = ByteArray(3)
        var rc = chan.readAvailable(buffer)
        assertEquals(3, rc)
        chan.close()

        rc = chan.readAvailable(buffer)
        assertEquals(2, rc)
    }

    @Test
    fun testReadAvailableNoSuspend() = runSuspendTest {
        val chan = SdkByteReadChannel("world!".encodeToByteArray())
        val buffer = SdkBuffer(16)
        buffer.write("hello, ")

        val rc = chan.readAvailable(buffer)
        assertEquals(6, rc)

        assertEquals("hello, world!", buffer.decodeToString())
    }

    @Test
    fun testReadAvailableSuspend() = runSuspendTest {
        val chan = SdkByteChannel()
        val job = launch {
            val buffer = SdkBuffer(16)
            buffer.write("hello, ")

            // should suspend
            val rc = chan.readAvailable(buffer)
            assertEquals(6, rc)

            assertEquals("hello, world!", buffer.decodeToString())
        }
        yield()

        // should resume
        chan.writeUtf8("world!")

        job.join()
        Unit
    }

    @Test
    fun testAwaitContent() = runSuspendTest {
        val chan = SdkByteChannel()
        var awaitingContent = false
        launch {
            awaitingContent = true
            chan.awaitContent()
            awaitingContent = false
        }

        yield()
        assertTrue(awaitingContent)
        chan.writeByte(1)
        yield()
        assertFalse(awaitingContent)
    }
}
