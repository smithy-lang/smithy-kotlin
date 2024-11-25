/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.testing.RandomTempFile
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.IOException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DATA_SIZE = 1024 * 12 + 13

class StreamingRequestBodyTest {
    @Test
    fun testWriteTo() = runTest {
        val content = ByteArray(DATA_SIZE) { it.toByte() }
        val expectedSha256 = content.sha256().encodeToHex()
        val chan = SdkByteReadChannel(content)
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val callContext = coroutineContext + Job()
        val actual = StreamingRequestBody(body, callContext)

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
        val replayableBody = object : HttpBody.ChannelContent() {
            override val isOneShot: Boolean = false
            override val contentLength: Long = 4
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val testContext = EmptyCoroutineContext + Job()

        val actualReplayable = StreamingRequestBody(replayableBody, testContext)
        assertFalse(actualReplayable.isOneShot())

        val oneshotBody = object : HttpBody.ChannelContent() {
            override val isOneShot: Boolean = true
            override val contentLength: Long = 4
            override fun readFrom(): SdkByteReadChannel = chan
        }
        val actualOneshot = StreamingRequestBody(oneshotBody, testContext)
        assertTrue(actualOneshot.isOneShot())
    }

    @Test
    fun testChannelCancelled(): Unit = runBlocking {
        val content = ByteArray(1024) { it.toByte() }
        val chan = SdkByteChannel(autoFlush = true)
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val callJob = Job()
        val callContext = coroutineContext + callJob
        val actual = StreamingRequestBody(body, callContext)

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
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = content.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val job = launch(Dispatchers.IO) {
            val callContext = coroutineContext + Job(coroutineContext.job)
            val actual = StreamingRequestBody(body, callContext)
            val buffer = Buffer()
            actual.writeTo(buffer)
        }

        delay(100.milliseconds)

        job.cancel()
        withTimeout(2.seconds) {
            // See https://github.com/smithy-lang/smithy-kotlin/issues/739
            // writeTo() should end up blocked waiting for data that will never come.
            // If the job used in the implementation isn't tied to the parent coroutine correctly
            // it will block forever
            job.join()
        }
    }

    @Test
    fun testDuplexWriteTo() = runTest {
        // basic sanity tests that we move this work into a background coroutine
        val content = ByteArray(DATA_SIZE) { it.toByte() }
        val expectedSha256 = content.sha256().encodeToHex()
        val chan = SdkByteChannel()
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long? = null
            override val isDuplex: Boolean = true
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val sink = Buffer()

        val callJob = Job()
        val callContext = coroutineContext + callJob
        val actual = StreamingRequestBody(body, callContext)

        assertTrue(actual.isDuplex())

        // assertEquals(1, callJob.children.toList().size) // producerJob
        // assertEquals(0, callJob.children.toList()[0].children.toList().size)
        actual.writeTo(sink)
        // assertEquals(1, callJob.children.toList()[0].children.toList().size) // writer
        // assertEquals(0, sink.size)
        chan.writeAll(content.source())

        chan.close()
        callJob.complete()
        callJob.join()

        val actualSha256 = sink.sha256().hex()
        assertEquals(expectedSha256, actualSha256)
    }

    @Test
    fun testDuplexWriteException() = runBlocking {
        val content = ByteArray(DATA_SIZE) { it.toByte() }
        val chan = SdkByteChannel()
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long? = null
            override val isDuplex: Boolean = true
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val sink = Buffer()

        val callJob = Job()
        val callContext = coroutineContext + callJob
        val actual = StreamingRequestBody(body, callContext)

        assertTrue(actual.isDuplex())

        // assertEquals(1, callJob.children.toList().size) // producerJob
        // assertEquals(0, callJob.children.toList()[0].children.toList().size)
        actual.writeTo(sink)
        // assertEquals(1, callJob.children.toList()[0].children.toList().size) // writer

        assertEquals(0, sink.size)
        assertFalse(chan.isClosedForWrite)
        assertFalse(callJob.isCancelled)

        val breakIndex = 1024L * 9 + 509

        val contentSource = content.source().brokenAt(breakIndex)
        assertThrows<SomeIoException> {
            CoroutineScope(CoroutineExceptionHandler { ctx, e -> println("Got exception $e") }).async {
                chan.writeAll(contentSource)
            }.await()
        }

        assertEquals(breakIndex, sink.size)
        assertFalse(chan.isClosedForWrite)
        assertFalse(callJob.isCancelled)
    }

    @Test
    fun testSdkSourceBody() = runTest {
        val file = RandomTempFile(32 * 1024)

        val body = object : HttpBody.SourceContent() {
            override val contentLength: Long = file.length()
            override val isOneShot: Boolean = false
            override fun readFrom(): SdkSource = file.source()
        }

        val callContext = coroutineContext + Job()
        val actual = StreamingRequestBody(body, callContext)

        val sink = Buffer()
        actual.writeTo(sink)

        assertContentEquals(file.readBytes(), sink.readByteArray())
    }
}

private class BrokenSource(private val delegate: SdkSource, breakOffset: Long) : SdkSource by delegate {
    private var bytesUntilBreak = breakOffset

    override fun read(sink: SdkBuffer, limit: Long): Long {
        val byteLimit = minOf(limit, bytesUntilBreak)

        return if (byteLimit > 0) {
            println("Requested $limit bytes, limiting to $byteLimit")
            delegate.read(sink, byteLimit).also { bytesUntilBreak -= it }
        } else if (limit > 0) {
            println("Reached breaking point, throwing SomeIoException")
            throw SomeIoException()
        } else {
            println("Requested 0 bytes? 🤔")
            0
        }
    }
}

private fun SdkSource.brokenAt(offset: Long) = BrokenSource(this, offset)

private class SomeIoException : IOException()
