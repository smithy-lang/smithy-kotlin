/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.request.*
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpRequestBuilderTest {
    @Test
    fun itBuilds() {
        val builder = HttpRequestBuilder()
        builder.headers {
            append("x-foo", "bar")
        }

        builder.url {
            host = "test.amazon.com"
        }

        builder.header("x-baz", "quux")

        val request = builder.build()
        assertEquals("bar", request.headers["x-foo"])
        assertEquals("quux", request.headers["x-baz"])
        assertEquals("test.amazon.com", request.url.host)
        assertEquals(HttpBody.Empty, request.body)
    }

    @Test
    fun testDumpRequest() = runSuspendTest {
        val content = "Mom!...Dad!...Bingo!...Bluey!"
        val builder = HttpRequestBuilder().apply {
            url {
                host = "test.amazon.com"
                path = "/debug/test"
                parameters.append("foo", "bar")
            }
            headers {
                append("x-baz", "quux")
                append("x-baz", "qux")
            }

            // test streaming bodies get replaced
            val chan = SdkByteReadChannel(content.encodeToByteArray())
            val stream = object : ByteStream.OneShotStream() {
                override val contentLength: Long = content.length.toLong()
                override fun readFrom(): SdkByteReadChannel = chan
            }
            body = stream.toHttpBody()
        }

        assertTrue(builder.body is HttpBody.Streaming)
        dumpRequest(builder, false)
        assertTrue(builder.body is HttpBody.Streaming)

        val actual = dumpRequest(builder, true)
        assertTrue(builder.body is HttpBody.Bytes)
        val expected = "GET /debug/test?foo=bar\r\nHost: test.amazon.com\r\nContent-Length: ${content.length}\r\nx-baz: quux;qux\r\n\r\n$content"
        assertEquals(expected, actual)
    }
}
