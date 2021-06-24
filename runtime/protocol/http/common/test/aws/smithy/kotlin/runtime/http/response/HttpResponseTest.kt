/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.*

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
            HttpBody.Empty
        )

        assertEquals("v1", resp.header("foo"))
        assertEquals(listOf("v1", "v2"), resp.getAllHeaders("foo"))
        assertEquals(HttpStatusCode.BadRequest, resp.statusCode())
    }

    @Test
    fun testDumpResponse() = runSuspendTest {
        val content = "Mom!...Dad!...Bingo!...Bluey!"
        val chan = SdkByteReadChannel(content.encodeToByteArray())
        val stream = object : ByteStream.Reader() {
            override val contentLength: Long = content.length.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val resp = HttpResponse(
            HttpStatusCode.OK,
            Headers {
                append("x-foo", "bar")
            },
            body = stream.toHttpBody()
        )

        val (c1, _) = dumpResponse(resp, false)
        assertSame(resp, c1)

        val (c2, actual) = dumpResponse(resp, true)
        assertTrue(c2.body is HttpBody.Bytes)
        assertNotSame(resp, c2)

        val expected = "HTTP/ 200: OK\r\nx-foo: bar\r\n\r\n$content"
        assertEquals(expected, actual)
    }
}
