/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import java.lang.RuntimeException
import kotlin.test.*

class ByteStreamBufferFlowTest : ByteStreamFlowTest(ByteStreamFactory.BYTE_ARRAY)
class ByteStreamSourceStreamFlowTest : ByteStreamFlowTest(ByteStreamFactory.SDK_SOURCE)
class ByteStreamChannelSourceFlowTest : ByteStreamFlowTest(ByteStreamFactory.SDK_CHANNEL)

abstract class ByteStreamFlowTest(
    private val factory: ByteStreamFactory,
) {
    @Test
    fun testToFlowWithSizeHint() = runTest {
        val data = "a korf is a tiger".repeat(1024).encodeToByteArray()
        val bufferSize = 8182 * 2
        val byteStream = factory.byteStream(data)
        val flow = byteStream.toFlow(bufferSize.toLong())
        val buffers = mutableListOf<ByteArray>()
        flow.toList(buffers)

        val totalCollected = buffers.fold(0) { acc, bytes -> acc + bytes.size }
        assertEquals(data.size, totalCollected)

        if (byteStream is ByteStream.Buffer) {
            assertEquals(1, buffers.size)
            assertContentEquals(data, buffers.first())
        } else {
            val expectedFullBuffers = data.size / bufferSize
            for (i in 0 until expectedFullBuffers) {
                val b = buffers[i]
                val expected = data.sliceArray((i * bufferSize)until (i * bufferSize + bufferSize))
                assertEquals(bufferSize, b.size)
                assertContentEquals(expected, b)
            }

            val last = buffers.last()
            val expected = data.sliceArray(((buffers.size - 1) * bufferSize) until data.size)
            assertContentEquals(expected, last)
        }
    }

    class FlowToByteStreamTest {
        private fun randomByteArray(size: Int): ByteArray = ByteArray(size) { i -> i.toByte() }

        val data = listOf(
            randomByteArray(576),
            randomByteArray(9172),
            randomByteArray(3278),
        )

        @Test
        fun testFlowToByteStreamReadAll() = runTest {
            val flow = data.asFlow()
            val scope = CoroutineScope(coroutineContext)
            val byteStream = flow.toByteStream(scope)

            assertNull(byteStream.contentLength)

            val actual = byteStream.toByteArray()
            val expected = data.reduce { acc, bytes -> acc + bytes }
            assertContentEquals(expected, actual)
        }

        @Test
        fun testContentLengthOverflow() = runTest {
            val advertisedContentLength = 1024L
            testInvalidContentLength(advertisedContentLength, "9748 bytes collected from flow exceeds reported content length of 1024")
        }

        @Test
        fun testContentLengthUnderflow() = runTest {
            val advertisedContentLength = data.fold(0L) { acc, bytes -> acc + bytes.size } + 100L
            testInvalidContentLength(advertisedContentLength, "expected 13126 bytes collected from flow, got 13026")
        }

        private suspend fun testInvalidContentLength(advertisedContentLength: Long, expectedMessage: String) {
            val job = Job()
            val uncaughtExceptions = mutableListOf<Throwable>()
            val exHandler = CoroutineExceptionHandler { _, throwable -> uncaughtExceptions.add(throwable) }
            val scope = CoroutineScope(job + exHandler)
            val byteStream = data
                .asFlow()
                .toByteStream(scope, advertisedContentLength)

            assertEquals(advertisedContentLength, byteStream.contentLength)

            val ex = assertFailsWith<IllegalStateException> {
                byteStream.toByteArray()
            }

            ex.message?.shouldContain(expectedMessage)
            assertTrue(job.isCancelled)
            job.join()

            assertEquals(1, uncaughtExceptions.size)
        }

        @Test
        fun testScopeCancellation() = runTest {
            // cancelling the scope should close/cancel the channel
            val waiter = Channel<Unit>(1)
            val flow = flow {
                emit(randomByteArray(128))
                emit(randomByteArray(277))
                waiter.receive()
                emit(randomByteArray(97))
            }

            val job = Job()
            val scope = CoroutineScope(job)
            val byteStream = flow.toByteStream(scope)
            assertIs<ByteStream.ChannelStream>(byteStream)
            assertNull(byteStream.contentLength)
            yield()

            job.cancel("scope cancelled")
            waiter.send(Unit)
            job.join()

            val ch = byteStream.readFrom()
            assertTrue(ch.isClosedForRead)
            assertTrue(ch.isClosedForWrite)
            assertIs<CancellationException>(ch.closedCause)
            ch.closedCause?.message.shouldContain("scope cancelled")
        }

        @Test
        fun testChannelCancellation() = runTest {
            // cancelling the channel should cancel the scope (via write failing)
            val waiter = Channel<Unit>(1)
            val flow = flow {
                emit(randomByteArray(128))
                emit(randomByteArray(277))
                waiter.receive()
                emit(randomByteArray(97))
            }

            val uncaughtExceptions = mutableListOf<Throwable>()
            val exHandler = CoroutineExceptionHandler { _, throwable -> uncaughtExceptions.add(throwable) }
            val job = Job()
            val scope = CoroutineScope(job + exHandler)
            val byteStream = flow.toByteStream(scope)
            assertIs<ByteStream.ChannelStream>(byteStream)

            val ch = byteStream.readFrom()
            val cause = RuntimeException("chan cancelled")
            ch.cancel(cause)

            // unblock the flow
            waiter.send(Unit)

            job.join()
            assertTrue(job.isCancelled)
            assertEquals(1, uncaughtExceptions.size)
            uncaughtExceptions.first().message.shouldContain("chan cancelled")
        }
    }
}
