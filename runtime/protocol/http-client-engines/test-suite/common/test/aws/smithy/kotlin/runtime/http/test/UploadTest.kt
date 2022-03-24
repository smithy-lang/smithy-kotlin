/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.encodeToHex
import aws.smithy.kotlin.runtime.util.sha256
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadTest : AbstractEngineTest() {
    @Test
    fun testUploadIntegrity() = testEngines {
        // test that what we write the entire contents given to us
        test { env, client ->
            val data = ByteArray(16 * 1024 * 1023) { it.toByte() }
            val sha = data.sha256().encodeToHex()

            val req = HttpRequest {
                method = HttpMethod.POST
                url(env.testServer)
                url.path = "/upload/content"
                body = ByteArrayContent(data)
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)
            assertEquals(sha, call.response.headers["content-sha256"])
        }
    }

    @Test
    fun testUploadWithDelay() = testEngines {
        test { env, client ->
            val data = ByteArray(16 * 1024 * 1024) { it.toByte() }
            val sha = data.sha256().encodeToHex()
            val ch = SdkByteChannel(autoFlush = true)
            val content = object : HttpBody.Streaming() {
                override val contentLength: Long = data.size.toLong()
                override fun readFrom(): SdkByteReadChannel = ch
            }

            val req = HttpRequest {
                method = HttpMethod.POST
                url(env.testServer)
                url.path = "/upload/content"
                body = content
            }

            coroutineScope {
                launch {
                    val end = data.size / 3
                    val slice = data.sliceArray(0 until end)
                    ch.writeFully(slice, 0, end)
                    delay(1000)
                    ch.writeFully(data, offset = end)
                    ch.close()
                }
                val call = client.call(req)
                call.complete()
                assertEquals(HttpStatusCode.OK, call.response.status)
                assertEquals(sha, call.response.headers["content-sha256"])
            }
        }
    }
}
