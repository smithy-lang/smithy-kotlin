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
import aws.smithy.kotlin.runtime.operation.ExecutionContext
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
        val actualNoContent = dumpRequest(builder, ExecutionContext(), false)
        val expectedNoContent = "GET /debug/test?foo=bar\r\nHost: test.amazon.com\r\nContent-Length: ${content.length}\r\nx-baz: quux;qux\r\n\r\n"
        assertTrue(builder.body is HttpBody.ChannelContent)
        assertEquals(expectedNoContent, actualNoContent)

        val actualWithContent = dumpRequest(builder, ExecutionContext(), true)
        assertTrue(builder.body is HttpBody.SourceContent)
        val expectedWithContent = "$expectedNoContent$content"
        assertEquals(expectedWithContent, actualWithContent)

        val actualReplacedContent = assertNotNull(builder.body.readAll()?.decodeToString())
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

        val actual = dumpRequest(req.toBuilder(), ExecutionContext(), true)
        val expected = "POST /debug/test?q1=foo\r\nHost: test.amazon.com\r\nContent-Length: 6\r\nx-baz: bar\r\nx-quux: qux\r\n\r\nfoobar"
        assertEquals(expected, actual)
    }

    @Test
    fun testDumpRequestRedactsHeaders() = runTest {
        val builder = HttpRequestBuilder().apply {
            url {
                host = Host.Domain("test.amazon.com")
                path.encoded = "/debug/test"
            }
            headers {
                append("Authorization", "AWS4-HMAC-SHA256 Credential=...")
                append("X-Amz-Security-Token", "secret-token")
                append("x-safe-header", "visible-value")
            }
        }

        // Header names are normalized to lowercase by HeadersBuilder (backed by CaseInsensitiveMap)
        val context = ExecutionContext().apply {
            set(LOG_REDACTED_HEADERS_KEY, setOf("Authorization", "X-Amz-Security-Token"))
        }
        val actual = dumpRequest(builder, context, false)
        val expected = "GET /debug/test\r\nHost: test.amazon.com\r\nauthorization: *** Sensitive Data Redacted ***\r\nx-amz-security-token: *** Sensitive Data Redacted ***\r\nx-safe-header: visible-value\r\n\r\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testDumpRequestRedactsCaseInsensitive() = runTest {
        val builder = HttpRequestBuilder().apply {
            url {
                host = Host.Domain("test.amazon.com")
                path.encoded = "/test"
            }
            headers {
                append("authorization", "secret")
                append("x-custom", "visible")
            }
        }

        // Redaction set uses mixed case but still matches lowercase keys from HeadersBuilder
        val context = ExecutionContext().apply {
            set(LOG_REDACTED_HEADERS_KEY, setOf("Authorization"))
        }
        val actual = dumpRequest(builder, context, false)
        val expected = "GET /test\r\nHost: test.amazon.com\r\nauthorization: *** Sensitive Data Redacted ***\r\nx-custom: visible\r\n\r\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testDumpRequestNoRedactionByDefault() = runTest {
        val builder = HttpRequestBuilder().apply {
            url {
                host = Host.Domain("test.amazon.com")
                path.encoded = "/test"
            }
            headers {
                // Appended as "Authorization" but stored lowercase by HeadersBuilder
                append("Authorization", "AWS4-HMAC-SHA256 Credential=...")
            }
        }

        val actual = dumpRequest(builder, ExecutionContext(), false)
        val expected = "GET /test\r\nHost: test.amazon.com\r\nauthorization: AWS4-HMAC-SHA256 Credential=...\r\n\r\n"
        assertEquals(expected, actual)
    }
}
