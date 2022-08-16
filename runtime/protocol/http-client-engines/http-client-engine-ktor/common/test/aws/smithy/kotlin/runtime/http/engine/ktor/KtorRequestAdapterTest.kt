/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.util.net.Host
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import io.ktor.content.ByteArrayContent as KtorByteArrayContent

@OptIn(ExperimentalCoroutinesApi::class)
class KtorRequestAdapterTest {
    @Test
    fun itStripsContentTypeHeader() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
        sdkBuilder.header("Content-Type", "application/json")
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        assertFalse("Content-Type" in actual.headers)

        val body = actual.body
        assertIs<OutgoingContent.NoContent>(body)
        assertEquals(ContentType.parse("application/json"), body.contentType)
    }

    @Test
    fun itConvertsHttpBodyVariantBytes() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
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
    fun itConvertsHttpBodyVariantStreaming() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()
        val sdkSource = ByteReadChannel(content)

        sdkBuilder.body = KtorHttpBody(content.size.toLong(), sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent
        assertEquals(ContentType.Application.OctetStream, convertedBody.contentType)
    }

    @Test
    fun itTransfersAStreamingBody() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(content.size.toLong(), sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteArray(256)
        val rc = channel.readAvailable(buffer)
        assertEquals(content.size, rc)
        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun itHandlesPartialStreamReads() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val content = "testing".toByteArray()

        val sdkSource = ByteReadChannel(content)
        sdkBuilder.body = KtorHttpBody(content.size.toLong(), sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteArray(5)
        val rc = channel.readAvailable(buffer)
        assertEquals(5, rc)
        assertEquals(2, channel.availableForRead)
    }

    @Test
    fun itHandlesStreamBackpressure() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
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
        sdkBuilder.body = KtorHttpBody(content.size.toLong(), sdkSource)
        val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
        val convertedBody = actual.body as OutgoingContent.ReadChannelContent

        val channel = convertedBody.readFrom()
        val buffer = ByteArray(2048)

        var totalRead = 0
        var readLoopCnt = 0
        while (!channel.isClosedForRead) {
            val rc = channel.readAvailable(buffer)
            totalRead += rc
            readLoopCnt++
        }

        assertEquals(content.size, totalRead)
        readLoopCnt.shouldBeGreaterThanOrEqual(4)
    }

    @Test
    fun itHandlesStreamCancellation() = runTest {
        val sdkBuilder = HttpRequestBuilder()
        sdkBuilder.url { host = Host.Domain("test.aws.com") }
        sdkBuilder.header("Content-Type", "application/octet-stream")
        val sdkSource = ByteChannel()
        sdkBuilder.body = KtorHttpBody(0L, sdkSource)

        val job = launch {
            val actual = KtorRequestAdapter(sdkBuilder, coroutineContext).toBuilder()
            val convertedBody = actual.body as OutgoingContent.ReadChannelContent
            val channel = convertedBody.readFrom()
            channel.readRemaining()
        }

        yield()

        job.cancel()
        yield()
        job.join()

        // if test runs to completion it worked. The "fill" coroutine that pulls
        // data from the source and propagates to the channel would otherwise prevent
        // an exit.
    }
}
