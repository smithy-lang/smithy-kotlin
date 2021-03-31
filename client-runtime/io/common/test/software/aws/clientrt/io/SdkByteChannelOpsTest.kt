/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import software.aws.clientrt.testing.runSuspendTest
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
}
