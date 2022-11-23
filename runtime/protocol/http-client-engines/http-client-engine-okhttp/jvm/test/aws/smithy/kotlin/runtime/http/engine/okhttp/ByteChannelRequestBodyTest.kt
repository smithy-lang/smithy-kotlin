/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ByteChannelRequestBodyTest {
    @Test
    fun testWriteTo() = runTest {
        val content = ByteArray(1024 * 12 + 13) { it.toByte() }
        val expectedSha256 = content.sha256().encodeToHex()
        val chan = SdkByteReadChannel(content)
        val body = object : HttpBody.Streaming() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val callContext = coroutineContext + Job()
        val actual = ByteChannelRequestBody(body, callContext)

        assertEquals(body.contentLength, actual.contentLength())
        assertFalse(actual.isDuplex())
        assertTrue(actual.isOneShot())

        val buffer = Buffer()
        actual.writeTo(buffer)
        // writeTo should block until all the body content is consumed
        val actualSha256 = buffer.sha256().hex()
        assertEquals(expectedSha256, actualSha256)
    }

    @Test
    fun testIsOneShot() {
        val chan = SdkByteReadChannel("test".encodeToByteArray())
        val replayableBody = object : HttpBody.Streaming() {
            override val isReplayable: Boolean = true
            override val contentLength: Long = 4
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val testContext = EmptyCoroutineContext + Job()

        val actualReplayable = ByteChannelRequestBody(replayableBody, testContext)
        assertFalse(actualReplayable.isOneShot())

        val oneshotBody = object : HttpBody.Streaming() {
            override val isReplayable: Boolean = false
            override val contentLength: Long = 4
            override fun readFrom(): SdkByteReadChannel = chan
        }
        val actualOneshot = ByteChannelRequestBody(oneshotBody, testContext)
        assertTrue(actualOneshot.isOneShot())
    }

    @Test
    fun testChannelCancelled(): Unit = runBlocking {
        val content = ByteArray(1024) { it.toByte() }
        val chan = SdkByteChannel(autoFlush = true)
        val body = object : HttpBody.Streaming() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val callJob = Job()
        val callContext = coroutineContext + callJob
        val actual = ByteChannelRequestBody(body, callContext)

        val job = launch(Dispatchers.IO) {
            val buffer = Buffer()
            actual.writeTo(buffer)
        }
        delay(100.milliseconds)

        val child = callJob.children.first()
        assertTrue(child.isActive)

        chan.close()
        job.join()
        assertTrue(child.isCompleted)
    }

    @Test
    fun testJobCancelled(): Unit = runBlocking {
        val content = ByteArray(1024) { it.toByte() }
        val chan = SdkByteChannel(autoFlush = true)
        val body = object : HttpBody.Streaming() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val job = launch(Dispatchers.IO) {
            val callContext = coroutineContext + Job(coroutineContext.job)
            val actual = ByteChannelRequestBody(body, callContext)
            val buffer = Buffer()
            // see https://github.com/awslabs/aws-sdk-kotlin/issues/733 for why we expect
            // this to be an IOException
            val ex = assertFailsWith<IOException> {
                actual.writeTo(buffer)
            }
            assertIs<CancellationException>(ex.cause)
        }
        delay(100.milliseconds)

        job.cancel()
        var tryCount = 0
        val startMs = System.currentTimeMillis()
        while (!job.isCompleted && tryCount < 120) {
            tryCount++
            try {
                withTimeout(1.seconds) {
                    // writeTo() should end up blocked waiting for data that will never come.
                    // If the job used in the implementation isn't tied to the parent coroutine correctly
                    // it will block forever
                    job.join()
                    val totalMs = System.currentTimeMillis() - startMs
                    println("Completed after $tryCount tries, $totalMs millseconds")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                println("Tried $tryCount times so far, still not done...")
            }
        }
        assertEquals(1, tryCount, "Took more than one second for job to finish. No es bueno.")
    }

    @Test
    fun testDuplexWriteTo() = runTest {
        // basic sanity tests that we move this work into a background coroutine
        val content = ByteArray(1024 * 12 + 13) { it.toByte() }
        val expectedSha256 = content.sha256().encodeToHex()
        val chan = SdkByteChannel()
        val body = object : HttpBody.Streaming() {
            override val contentLength: Long? = null
            override val isDuplex: Boolean = true
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val sink = TestSink()

        val callJob = Job()
        val callContext = coroutineContext + callJob
        val actual = ByteChannelRequestBody(body, callContext)

        assertTrue(actual.isDuplex())

        assertEquals(1, callJob.children.toList().size) // producerJob
        assertEquals(0, callJob.children.toList()[0].children.toList().size)
        actual.writeTo(sink)
        assertEquals(1, callJob.children.toList()[0].children.toList().size)
        assertEquals(sink.buffer.size, 0)
        chan.writeFully(content)

        assertFalse(sink.isClosed)

        chan.close()
        callJob.complete()
        callJob.join()

        // we must manually close the sink given to us when stream completes
        assertTrue(sink.isClosed)

        val actualSha256 = sink.buffer.sha256().hex()
        assertEquals(expectedSha256, actualSha256)
    }
}

private class TestSink(override val buffer: Buffer = Buffer()) : BufferedSink by buffer {
    var isClosed = false
    override fun close() { isClosed = true }
}
