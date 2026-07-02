/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpResponseTest {
    @Test
    fun testProtocolResponseExtensions() {
        val resp = HttpResponse(
            HttpStatusCode.BadRequest,
            Headers {
                append("foo", "v1")
                append("foo", "v2")
                append("bar", "v3")
            },
            HttpBody.Empty,
        )

        assertEquals("v1", resp.header("foo"))
        assertEquals(listOf("v1", "v2"), resp.getAllHeaders("foo"))
        assertEquals(HttpStatusCode.BadRequest, resp.statusCode())
    }

    @Test
    fun testDumpResponse() = runTest {
        val content = "Mom!...Dad!...Bingo!...Bluey!"
        val chan = SdkByteReadChannel(content.encodeToByteArray())
        val stream = object : ByteStream.ChannelStream() {
            override val contentLength: Long = content.length.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val resp = HttpResponse(
            HttpStatusCode.OK,
            Headers {
                append("x-foo", "bar")
            },
            body = stream.toHttpBody(),
        )

        val (c1, _) = dumpResponse(resp, false)
        assertSame(resp, c1)

        val (c2, actual) = dumpResponse(resp, true)
        assertTrue(c2.body is HttpBody.Bytes)
        assertNotSame(resp, c2)

        val expected = "HTTP 200: OK\r\nx-foo: bar\r\n\r\n$content"
        assertEquals(expected, actual)
    }

    @Test
    fun testDumpResponseRedactsHeaders() = runTest {
        val resp = HttpResponse(
            HttpStatusCode.OK,
            Headers {
                append("x-amz-security-token", "secret-token")
                append("x-request-id", "abc-123")
            },
            HttpBody.Empty,
        )

        val redacted = setOf("X-Amz-Security-Token")
        val (_, actual) = dumpResponse(resp, false, redacted)
        val expected = "HTTP 200: OK\r\nx-amz-security-token: <REDACTED>\r\nx-request-id: abc-123\r\n\r\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testDumpResponseNoRedactionByDefault() = runTest {
        val resp = HttpResponse(
            HttpStatusCode.OK,
            Headers {
                // Appended as "Authorization" but stored lowercase by Headers (backed by CaseInsensitiveMap)
                append("Authorization", "Bearer token123")
            },
            HttpBody.Empty,
        )

        val (_, actual) = dumpResponse(resp, false)
        val expected = "HTTP 200: OK\r\nauthorization: Bearer token123\r\n\r\n"
        assertEquals(expected, actual)
    }
}
