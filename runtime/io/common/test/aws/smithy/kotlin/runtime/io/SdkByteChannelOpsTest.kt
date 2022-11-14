/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SdkByteChannelOpsTest {

    @Test
    fun testCopyTo() = runTest {
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
    fun testCopyToFallback() = runTest {
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
    fun testCopyToSameOrZero() = runTest {
        val chan = SdkByteChannel(false)
        assertFailsWith<IllegalArgumentException> {
            chan.copyTo(chan)
        }
        val dst = SdkByteChannel(false)
        assertEquals(0, chan.copyTo(dst, limit = 0))
    }

    @Test
    fun testReadFromClosedChannel() = runTest {
        val chan = SdkByteReadChannel(byteArrayOf(1, 2, 3, 4, 5))
        val buffer = ByteArray(3)
        var rc = chan.readAvailable(buffer)
        assertEquals(3, rc)
        chan.close()

        rc = chan.readAvailable(buffer)
        assertEquals(2, rc)
    }

    @Test
    fun testReadAvailableNoSuspend() = runTest {
        val chan = SdkByteReadChannel("world!".encodeToByteArray())
        val buffer = SdkBuffer()
        buffer.writeUtf8("hello, ")

        val rc = chan.readAvailable(buffer, Long.MAX_VALUE)
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
            val rc = chan.readAvailable(buffer, Long.MAX_VALUE)
            assertEquals(6, rc)

            assertEquals("hello, world!", buffer.readUtf8())
        }
        yield()

        // should resume
        chan.writeUtf8("world!")

        job.join()
        Unit
    }

    @Test
    fun testAwaitContent() = runTest {
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

    @Test
    fun testReadUtf8Chars() = runTest {
        val chan = SdkByteReadChannel("hello".encodeToByteArray())
        assertEquals('h', chan.readUtf8CodePoint()?.toChar())
        assertEquals('e', chan.readUtf8CodePoint()?.toChar())
        assertEquals('l', chan.readUtf8CodePoint()?.toChar())
        assertEquals('l', chan.readUtf8CodePoint()?.toChar())
        assertEquals('o', chan.readUtf8CodePoint()?.toChar())
        assertNull(chan.readUtf8CodePoint())
    }

    @Test
    fun testReadMultibyteUtf8Chars() = runTest {
        // https://www.fileformat.info/info/unicode/char/1d122/index.htm
        // $ - 1 byte, cent sign - 2bytes, euro sign - 3 bytes, musical clef - 4 points (surrogate pair)
        val content = "$¢€\uD834\uDD22"
        val chan = SdkByteReadChannel(content.encodeToByteArray())

        val expected = listOf(
            36, // $
            162, // ¢
            8364, // €
            119074, // musical F clef
        )

        expected.forEachIndexed { i, exp ->
            val code = chan.readUtf8CodePoint()
            assertEquals(exp, code, "[i=$i] expected $exp, found $code ")
        }
        assertNull(chan.readUtf8CodePoint())
    }
}
