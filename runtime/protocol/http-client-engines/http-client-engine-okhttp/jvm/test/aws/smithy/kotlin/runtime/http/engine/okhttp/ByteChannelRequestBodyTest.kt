/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

        val actualReplayable = ByteChannelRequestBody(replayableBody, EmptyCoroutineContext)
        assertFalse(actualReplayable.isOneShot())

        val oneshotBody = object : HttpBody.Streaming() {
            override val isReplayable: Boolean = false
            override val contentLength: Long = 4
            override fun readFrom(): SdkByteReadChannel = chan
        }
        val actualOneshot = ByteChannelRequestBody(oneshotBody, EmptyCoroutineContext)
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

        val callContext = coroutineContext + Job()
        val actual = ByteChannelRequestBody(body, callContext)

        val job = launch(Dispatchers.IO) {
            val buffer = Buffer()
            actual.writeTo(buffer)
        }
        delay(100.milliseconds)

        chan.close()
        job.join()
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
            actual.writeTo(buffer)
        }
        delay(100.milliseconds)

        job.cancel()
        withTimeout(1.seconds) {
            // writeTo() should end up blocked waiting for data that will never come.
            // If the job used in the implementation isn't tied to the parent coroutine correctly
            // it will block forever
            job.join()
        }
    }
}
