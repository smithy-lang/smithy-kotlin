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
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HttpRequestBuilderTest {
    @Test
    fun itBuilds() {
        val builder = HttpRequestBuilder()
        builder.headers {
            append("x-foo", "bar")
        }

        builder.url {
            host = Host.Domain("test.amazon.com")
        }

        builder.header("x-baz", "quux")

        val request = builder.build()
        assertEquals("bar", request.headers["x-foo"])
        assertEquals("quux", request.headers["x-baz"])
        assertEquals(Host.Domain("test.amazon.com"), request.url.host)
        assertEquals(HttpBody.Empty, request.body)
    }

    @Test
    fun testDumpRequest() = runTest {
        val content = "Mom!...Dad!...Bingo!...Bluey!"
        val builder = HttpRequestBuilder().apply {
            url {
                host = Host.Domain("test.amazon.com")
                path.encoded = "/debug/test"
                parameters.decodedParameters.add("foo", "bar")
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
        val actualNoContent = dumpRequest(builder, false)
        val expectedNoContent = "GET /debug/test?foo=bar\r\nHost: test.amazon.com\r\nContent-Length: ${content.length}\r\nx-baz: quux;qux\r\n\r\n"
        assertTrue(builder.body is HttpBody.ChannelContent)
        assertEquals(expectedNoContent, actualNoContent)

        val actualWithContent = dumpRequest(builder, true)
        assertTrue(builder.body is HttpBody.SourceContent)
        val expectedWithContent = "$expectedNoContent$content"
        assertEquals(expectedWithContent, actualWithContent)

        val actualReplacedContent = builder.body.readAll()?.decodeToString() ?: fail("expected content")
        assertEquals(content, actualReplacedContent)
    }

    @Test
    fun testRequestToBuilder() = runTest {
        val req = HttpRequest(
            method = HttpMethod.POST,
            url = Url {
                scheme = Scheme.HTTPS
                host = Host.Domain("test.amazon.com")
                path.decoded = "/debug/test"
                parameters.decodedParameters.add("q1", "foo")
            },
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
