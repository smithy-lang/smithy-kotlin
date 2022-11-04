/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.testing.ManualDispatchTestBase
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SdkByteChannelSuspendTest : ManualDispatchTestBase() {
    private val ch = SdkByteChannel()

    class TestException : RuntimeException("test exception")

    private suspend fun SdkByteChannel.write(str: String) {
        val source = SdkBuffer().apply { writeUtf8(str) }
        write(source)
    }

    @AfterTest
    fun finish() {
        ch.cancel(CancellationException("Test finished"))
    }

    @Test
    fun testReadBeforeAvailable() = runTest {
        // test readAvailable() suspends when no data is available
        expect(1)

        val data = "1234"
        val source = SdkBuffer().apply { writeUtf8(data) }

        launch {
            expect(3)
            val sink = SdkBuffer()

            // should suspend
            val rc = ch.read(sink, Long.MAX_VALUE)
            expect(5)
            assertEquals(data.length.toLong(), rc)
        }

        expect(2)
        yield()

        expect(4)

        // read continuation should be queued to resume
        ch.write(source)
        yield()

        finish(6)
    }

    @Test
    fun testReadAfterAvailable() = runTest {
        // test readAvailable() does NOT suspend when data is available
        expect(1)

        val source = SdkBuffer().apply { writeUtf8("1234") }
        ch.write(source)

        launch {
            expect(3)

            val sink = SdkBuffer()

            // should NOT suspend
            val rc = ch.read(sink, Long.MAX_VALUE)

            expect(4)
            assertEquals(4, rc)

            expect(5)
        }

        expect(2)
        yield()
        finish(6)
    }

    @Test
    fun testReadFullySuspends() = runTest {
        // test readFully() suspends when not enough data is available to satisfy the request
        expect(1)

        ch.write("1234")

        launch {
            expect(3)

            val sink = SdkBuffer()
            // should suspend
            ch.readFully(sink, byteCount = 8)

            assertEquals(8, sink.size)
            expect(6)
        }

        expect(2)
        yield()
        expect(4)
        ch.write("5678")

        expect(5)
        yield()

        finish(7)
    }

    @Test
    fun testReadAfterAvailableFully() = runTest {
        // test readFully() does NOT suspend when data is available to satisfy the request
        expect(1)

        ch.write("1234")

        launch {
            expect(3)

            val sink = SdkBuffer()

            // should NOT suspend
            ch.readFully(sink, byteCount = 4)

            expect(4)
        }

        expect(2)
        yield()

        finish(5)
    }

    @Test
    fun testReadToEmpty() = runTest {
        // test read() does not suspend when length is zero
        expect(1)

        val sink = SdkBuffer()
        val rc = ch.read(sink, 0)
        expect(2)
        assertEquals(0, rc)

        finish(3)
    }

    @Test
    fun testReadToEmptyFromFailedChannel() = runTest {
        expect(1)
        ch.cancel(TestException())
        val sink = SdkBuffer()
        assertFailsWith<TestException> {
            ch.read(sink, 0)
        }
        finish(2)
    }

    @Test
    fun testReadToEmptyFromClosedChannel() = runTest {
        expect(1)
        ch.close()
        val sink = SdkBuffer()
        val rc = ch.read(sink, 0)
        expect(2)
        assertEquals(-1, rc)
        finish(3)
    }

    @Test
    fun testReadFromFailedChannel() = runTest {
        expect(1)
        ch.cancel(TestException())
        assertFailsWith<TestException> {
            val sink = SdkBuffer()
            ch.read(sink, 1)
        }
        finish(2)
    }

    @Test
    fun testReadFromClosedChannelNoSuspend() = runTest {
        expect(1)
        ch.close()
        val sink = SdkBuffer()
        assertEquals(-1, ch.read(sink, 1))
        finish(2)
    }

    @Test
    fun testReadFromClosedChannelSuspend() = runTest {
        expect(1)
        val sink = SdkBuffer()
        launch {
            expect(2)
            val rc = ch.read(sink, 1)
            expect(4)
            assertEquals(-1, rc)
        }

        yield()
        expect(3)
        ch.close()

        yield()
        finish(5)
    }

    @Test
    fun testReadFullyFromFailedChannel() = runTest {
        expect(1)
        ch.cancel(TestException())
        assertFailsWith<TestException> {
            val sink = SdkBuffer()
            ch.readFully(sink, 1)
        }
        finish(2)
    }

    @Test
    fun testReadFullyFromClosedChannel() = runTest {
        expect(1)
        ch.close()
        assertFailsWith<EOFException> {
            val sink = SdkBuffer()
            ch.readFully(sink, 1)
        }
        finish(2)
    }

    @Test
    fun testReadState() = runTest {
        assertFalse(ch.isClosedForWrite)
        assertFalse(ch.isClosedForRead)
        assertEquals(0, ch.availableForRead)
        ch.write("1234")
        assertEquals(4, ch.availableForRead)
        ch.close()
        assertTrue(ch.isClosedForWrite)
        assertFalse(ch.isClosedForRead)

        val sink = SdkBuffer()
        val rc = ch.read(sink, Long.MAX_VALUE)
        assertEquals(4, rc)

        assertEquals(0, ch.availableForRead)
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testReadRemaining() = runTest {
        expect(1)
        ch.write("1234")
        launch {
            expect(3)
            val sink = SdkBuffer()
            ch.readRemaining(sink)
            assertEquals("1234", sink.readUtf8())
            expect(5)
        }

        expect(2)
        yield()
        expect(4)
        ch.close()
        yield()
        finish(6)
    }

    // FIXME - not working
    @Ignore
    @Test
    fun testReadInProgress() = runTest {
        expect(1)
        launch {
            expect(3)
            val sink1 = SdkBuffer()
            ch.read(sink1, Long.MAX_VALUE)
        }
        expect(2)
        yield()
        expect(4)

        assertFailsWith<IllegalStateException> {
            val sink2 = SdkBuffer()
            ch.read(sink2, Long.MAX_VALUE)
        }.message.shouldContain("Read operation already in progress")

        ch.close()
        finish(5)
    }

    @Test
    fun testReadFullyEof() = runTest {
        expect(1)
        ch.write("1234")
        val sink = SdkBuffer()
        launch {
            expect(3)
            assertFailsWith<EOFException> {
                ch.readFully(sink, 16)
            }.message.shouldContain("Unexpected EOF: expected 12 more bytes")
        }
        expect(2)
        yield()
        expect(4)

        ch.close()
        finish(5)
    }

    @Test
    fun testResumeReadFromFailedChannel() = runTest {
        expect(1)

        launch {
            expect(3)
            ch.cancel(TestException())
        }

        expect(2)
        assertFailsWith<TestException> {
            val sink = SdkBuffer()
            // should suspend and fail with the exception when resumed
            ch.read(sink, Long.MAX_VALUE)
        }
        finish(4)
    }

    @Test
    fun testResumeReadFromClosedChannelNoContent() = runTest {
        expect(1)

        launch {
            expect(3)
            ch.close()
        }

        expect(2)
        val sink = SdkBuffer()
        val rc = ch.read(sink, Long.MAX_VALUE)
        assertEquals(-1, rc)
        finish(4)
    }

    @Test
    fun testLargeTransfer() = runTest {
        val data = "a".repeat(262144) + "b".repeat(512)
        launch {
            ch.write(data)
            ch.close()
        }

        val buf = ch.readToBuffer()
        assertEquals(data.length.toLong(), buf.size)
        assertEquals(data, buf.readUtf8())
        assertEquals(data.length.toLong(), ch.totalBytesWritten)
    }
}
