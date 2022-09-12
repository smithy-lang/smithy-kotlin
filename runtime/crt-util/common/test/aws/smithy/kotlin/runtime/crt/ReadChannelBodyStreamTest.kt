/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.writeUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadChannelBodyStreamTest {
    private fun mutableBuffer(capacity: Int): Pair<MutableBuffer, ByteArray> {
        val dest = ByteArray(capacity)
        return MutableBuffer.of(dest) to dest
    }

    private fun body(chan: SdkByteReadChannel, contentLength: Number): HttpBody.Streaming =
        object : HttpBody.Streaming() {
            override val contentLength: Long = contentLength.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

    @Test
    fun testClose() = runTest {
        val chan = SdkByteChannel()
        val body = body(chan, 0)
        val (sendBuffer, _) = mutableBuffer(16)

        val stream = ReadChannelBodyStream(body, coroutineContext)
        // let the proxy get started
        yield()
        chan.close()

        // proxy should resume and signal close
        yield()

        assertTrue(stream.sendRequestBody(sendBuffer))
    }

    @Test
    fun testCancellation() = runTest {
        val chan = SdkByteChannel()
        val body = body(chan, 0)
        val job = Job()
        val stream = ReadChannelBodyStream(body, coroutineContext + job)

        job.cancel()

        val (sendBuffer, _) = mutableBuffer(16)
        assertFailsWith<CancellationException> {
            stream.sendRequestBody(sendBuffer)
        }
    }

    @Test
    fun testReadFully() = runTest {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val chan = SdkByteReadChannel(data)
        val body = body(chan, data.size)
        val stream = ReadChannelBodyStream(body, coroutineContext)
        yield()

        val (sendBuffer, sent) = mutableBuffer(16)
        val streamDone = stream.sendRequestBody(sendBuffer)
        assertTrue(streamDone)
        assertTrue {
            sent.sliceArray(data.indices).contentEquals(data)
        }
    }

    @Test
    fun testPartialRead() = runTest {
        val data = "123456".encodeToByteArray()
        val chan = SdkByteReadChannel(data)
        val body = body(chan, data.size)
        val stream = ReadChannelBodyStream(body, coroutineContext)
        yield()

        val (sendBuffer1, sent1) = mutableBuffer(3)
        var streamDone = stream.sendRequestBody(sendBuffer1)
        assertFalse(streamDone)
        assertEquals("123", sent1.decodeToString())
        assertEquals(0, sendBuffer1.writeRemaining)

        val (sendBuffer2, sent2) = mutableBuffer(3)
        streamDone = stream.sendRequestBody(sendBuffer2)
        assertTrue(streamDone)
        assertEquals("456", sent2.decodeToString())
    }

    @Test
    fun testLargeTransfer() = runTest {
        val chan = SdkByteChannel()

        val data = "foobar"
        val n = 10_000
        launch {
            val result = runCatching {
                repeat(n) {
                    chan.writeUtf8(data)
                }
            }

            chan.close(result.exceptionOrNull())
        }

        val body = body(chan, data.length * n)
        val stream = ReadChannelBodyStream(body, coroutineContext)
        yield()

        var totalBytesRead = 0
        val sendSize = 16
        do {
            val (sendBuffer, _) = mutableBuffer(sendSize)
            val streamDone = stream.sendRequestBody(sendBuffer)
            totalBytesRead += sendSize - sendBuffer.writeRemaining
            yield()
        } while (!streamDone)

        val expected = data.length * n
        assertEquals(expected, totalBytesRead)
    }

    @Test
    fun testRejectDuplexStream() = runTest {
        val body = object : HttpBody.Streaming() {
            override val isDuplex = true
            override val contentLength = 0L
            override fun readFrom() = SdkByteChannel()
        }

        assertFailsWith<IllegalStateException> { ReadChannelBodyStream(body, coroutineContext) }
    }

    @Test
    fun testRejectStreamWithoutExplicitContentLength() = runTest {
        val body = object : HttpBody.Streaming() {
            override val contentLength = null
            override fun readFrom() = SdkByteChannel()
        }

        assertFailsWith<IllegalArgumentException> { ReadChannelBodyStream(body, coroutineContext) }
    }
}
