/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.dumpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun testDumpRequest() = runTest {
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
            val stream = object : ByteStream.ChannelStream() {
                override val contentLength: Long = content.length.toLong()
                override fun readFrom(): SdkByteReadChannel = chan
            }
            body = stream.toHttpBody()
        }

        assertTrue(builder.body is HttpBody.ChannelContent)
        dumpRequest(builder, false)
        assertTrue(builder.body is HttpBody.ChannelContent)

        val actual = dumpRequest(builder, true)
        assertTrue(builder.body is HttpBody.Bytes)
        val expected = "GET /debug/test?foo=bar\r\nHost: test.amazon.com\r\nContent-Length: ${content.length}\r\nx-baz: quux;qux\r\n\r\n$content"
        assertEquals(expected, actual)
    }

    @Test
    fun testRequestToBuilder() = runTest {
        val req = HttpRequest(
            method = HttpMethod.POST,
            url = Url(
                Protocol.HTTPS,
                "test.amazon.com",
                path = "/debug/test",
                parameters = QueryParameters {
                    append("q1", "foo")
                },
            ),
            headers = Headers {
                append("x-baz", "bar")
                append("x-quux", "qux")
            },
            body = ByteArrayContent("foobar".encodeToByteArray()),
        )

        val actual = dumpRequest(req.toBuilder(), true)
        val expected = "POST /debug/test?q1=foo\r\nHost: test.amazon.com\r\nContent-Length: 6\r\nx-baz: bar\r\nx-quux: qux\r\n\r\nfoobar"
        assertEquals(expected, actual)
    }
}
