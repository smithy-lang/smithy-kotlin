/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine.ktor

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.ktor.content.ByteArrayContent as KtorByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.*
import org.junit.Test
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.header
import software.aws.clientrt.http.request.url

class KtorRequestAdapterTest {
    @Test
    fun `it strips Content-Type header`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/json")
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        actual.headers.contains("Content-Type").shouldBeFalse()
    }

    @Test
    fun `it converts HttpBody variant Bytes`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/json")
        val content = "testing".toByteArray()
        sdkBuilder.body = ByteArrayContent(content)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        actual.headers.contains("Content-Type").shouldBeFalse()
        val convertedBody = actual.body as KtorByteArrayContent
        assertEquals(ContentType.Application.Json, convertedBody.contentType)
        assertEquals(content, convertedBody.bytes())
    }

    @Test
    fun `it converts HttpBody variant Streaming`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()
        val sdkSource = ByteReadChannel(content)

        sdkBuilder.body = KtorHttpBody(sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent
        assertEquals(ContentType.Application.OctetStream, convertedBody.contentType)
    }

    @Test
    fun `it transfers a Streaming body`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteBuffer.allocate(256)
        channel.readAvailable(buffer)
        assertEquals(content.size, buffer.position())
        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun `it handles partial Stream reads`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteBuffer.allocate(5)
        channel.readAvailable(buffer)
        assertEquals(5, buffer.position())
        assertEquals(2, channel.availableForRead)
    }

    @Test
    fun `it handles Stream backpressure`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        // NOTE: this requires knowing some internal details like the buffer size in use
        // we should end up with two 4096 reads of the source content (and 2 equivalent writes to
        // the channel) inside the "fill" coroutine.
        //
        // The test side below represents a slow consumer and is only consuming (up to) 2048
        // bytes at a time which means we _should_ get at least 4 reads (it may be more than 4 depending
        // on how the coroutines interact and "availableForRead" is updated)
        //
        // There's no way to actually verify that the coroutine filling the channel from the source
        // content "slows" down and waits without printing it out but this is a good approximation
        // that there is coordination with reads/writes being unbalanced.
        val content = ByteArray(8192) { (it % 128).toByte() }

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteBuffer.allocate(2048)

        var totalRead = 0
        var readLoopCnt = 0
        while (!channel.isClosedForRead) {
            channel.readAvailable(buffer)
            totalRead += buffer.position()
            buffer.clear()
            readLoopCnt++
        }
        assertEquals(content.size, totalRead)
        readLoopCnt.shouldBeGreaterThanOrEqual(4)

        return@runBlocking Unit
    }

    @Test
    fun `it handles Stream cancellation`() = runBlocking {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = "test.aws.com" }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        // NOTE: Again sizing here takes some internal knowledge to ensure
        // the "fill" coroutine is forced into a waiting state.
        val content = ByteArray(8192) { (it % 128).toByte() }

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(sdkSource)

        val job = launch {
            val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
            val convertedBody = actual.body as OutgoingContent.ReadChannelContent

            val channel = convertedBody.readFrom()
            val buffer = ByteBuffer.allocate(2048)
            for (i in 0 until 4) {
                channel.readAvailable(buffer)
                buffer.clear()
            }
        }

        delay(500)
        job.cancel()
        job.join()

        // if test runs to completion it worked. The "fill" coroutine that pulls
        // data from the source and propagates to the channel would otherwise prevent
        // an exit.
        return@runBlocking Unit
    }
}
