/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadTest : AbstractEngineTest() {
    private val largeBodyData = ByteArray(32 * 1024 * 1024) { it.toByte() }
    private val largeBodySha = largeBodyData.sha256().encodeToHex()
    private fun testUploadIntegrity(
        expectedSha: String = largeBodySha,
        block: () -> HttpBody,
    ) = testEngines {
        // test that what we write the entire contents given to us
        test { env, client ->

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup(env)
                url.path = "/upload/content"
                body = block()
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)
            assertEquals(expectedSha, call.response.headers["content-sha256"])
        }
    }

    @Test
    fun testUploadIntegrityBufferContent() = testUploadIntegrity { HttpBody.fromBytes(largeBodyData) }

    @Test
    fun testUploadIntegrityChannelContent() = testUploadIntegrity {
        val ch = SdkByteReadChannel(largeBodyData)
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = largeBodyData.size.toLong()
            override fun readFrom(): SdkByteReadChannel = ch
        }
        body
    }

    @Test
    fun testUploadIntegritySourceContent() = testUploadIntegrity {
        val body = object : HttpBody.SourceContent() {
            override val contentLength: Long = largeBodyData.size.toLong()
            override fun readFrom(): SdkSource = largeBodyData.source()
        }
        body
    }

    @Test
    fun testUploadWithDelay() = testEngines {
        test { env, client ->
            val data = ByteArray(16 * 1024 * 1024) { it.toByte() }
            val sha = data.sha256().encodeToHex()
            val ch = SdkByteChannel(autoFlush = true)
            val content = object : HttpBody.ChannelContent() {
                override val contentLength: Long = data.size.toLong()
                override fun readFrom(): SdkByteReadChannel = ch
            }

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup(env)
                url.path = "/upload/content"
                body = content
            }

            coroutineScope {
                launch {
                    val end = data.size / 3
                    val slice = data.sliceArray(0 until end)
                    ch.write(slice, 0, end)
                    delay(1000)
                    ch.write(data, offset = end)
                    ch.close()
                }
                val call = client.call(req)
                call.complete()
                assertEquals(HttpStatusCode.OK, call.response.status)
                assertEquals(sha, call.response.headers["content-sha256"])
            }
        }
    }

    @Test
    fun testUploadWithClosingDelay() = testEngines {
        test { env, client ->
            val data = ByteArray(16) { it.toByte() }
            val sha = data.sha256().encodeToHex()
            val ch = SdkByteChannel(autoFlush = true)
            val content = object : HttpBody.ChannelContent() {
                override val contentLength: Long = data.size.toLong()
                override fun readFrom(): SdkByteReadChannel = ch
            }

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup(env)
                url.path = "/upload/content"
                body = content
            }

            coroutineScope {
                launch {
                    ch.write(data)
                    delay(1000)
                    // CRT will have stopped polling by now
                    ch.close()
                }
                val call = client.call(req)
                call.complete()
                assertEquals(HttpStatusCode.OK, call.response.status)
                assertEquals(sha, call.response.headers["content-sha256"])
            }
        }
    }

    @Test
    fun testUploadWithWrappedStream() = testEngines {
        // test custom ByteStream behavior
        // see https://github.com/awslabs/smithy-kotlin/issues/613
        test { env, client ->
            val data = ByteArray(1024 * 1024) { it.toByte() }
            val sha = data.sha256().encodeToHex()

            val wrappedStream = object : ByteStream.ChannelStream() {
                override val contentLength: Long = data.size.toLong()
                override fun readFrom(): SdkByteReadChannel {
                    val underlying = SdkByteReadChannel(data)
                    return object : SdkByteReadChannel by underlying {}
                }
            }

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup(env)
                url.path = "/upload/content"
                body = wrappedStream.toHttpBody()
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)
            assertEquals(sha, call.response.headers["content-sha256"], "sha mismatch for upload on ${client.engine}")
        }
    }

    @Test
    fun testUploadEmptyWithContentTypes() = testEngines {
        // test that empty bodies with a specified Content-Type actually include Content-Type in the request
        // see https://github.com/awslabs/aws-sdk-kotlin/issues/588
        test { env, client ->
            val req = HttpRequest {
                method = HttpMethod.POST
                headers {
                    append("Content-Type", "application/xml")
                }
                testSetup(env)
                url.path = "/upload/content"
                body = HttpBody.Empty
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)

            val reqContentType = call.response.headers["request-content-type"]
            assertEquals("application/xml", reqContentType, "No Content-Type set on ${client.engine}")
        }
    }

    /**
     * Test cancelling the request in the middle of processing the request body
     * This recreates [aws-sdk-kotlin#733](https://github.com/awslabs/aws-sdk-kotlin/issues/733)
     * but does not result in a test failure (instead you will see
     * 'Exception in thread "OkHttp Dispatcher" ... ' in the test output if not handled).
     *
     * If we exposed configuring the `Dispatcher` on OkHttpEngine builder then we could
     * plug in a custom executor/thread factory to make the actual assertions (in a JVM test sourceSet).
     * Although that would only work so long as okhttp continues to use `execute` rather than `submit` internally.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testUploadCancellation() = testEngines {
        test { env, client ->
            val chan = SdkByteChannel(autoFlush = true)

            val writeJob = GlobalScope.launch {
                val seedData = ByteArray(6017) { it.toByte() }
                chan.write(seedData)
            }

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup(env)
                url.path = "/upload/content"
                body = chan.toHttpBody(16 * 1024 * 1024)
            }

            val job = GlobalScope.launch {
                val call = client.call(req)
                try {
                    // wait for seed data to have been written
                    writeJob.join()
                    delay(5000)
                } finally {
                    call.complete()
                    chan.cancel(null)
                }
            }

            writeJob.join()
            job.cancelAndJoin()
        }
    }
}
