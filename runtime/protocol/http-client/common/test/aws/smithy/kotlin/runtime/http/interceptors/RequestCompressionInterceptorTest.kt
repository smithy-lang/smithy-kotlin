/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.Gzip
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RequestCompressionInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    @Test
    fun testNoCompressionBecauseOfThreshold() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                100000,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(null, call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }

    @Test
    fun testNoCompressionBecauseOfNoSupportedAlgorithm() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                0,
                listOf(),
                listOf(),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(null, call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }

    @Test
    fun testInvalidCompressionThreshold() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        assertFailsWith<IllegalArgumentException> {
            op.interceptors.add(
                RequestCompressionInterceptor(
                    -1,
                    listOf("gzip"),
                    listOf(Gzip()),
                ),
            )
        }
    }

    @Test
    fun testCompression() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertTrue(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0).contentEquals(call.request.body.readAll()),
        )
    }

    @Ignore // TODO: Re-enable test
    @Test
    fun testSdkByteReadChannelAlwaysCompressed() = runTest {
        val req = HttpRequestBuilder().apply {
            body = SdkByteReadChannel("<Foo>bar</Foo>".encodeToByteArray()).toHttpBody()
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                10000000,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0),
            call.request.body.readAll(),
        )
    }

    @Test
    fun testSdkSourceAlwaysCompressed() = runTest {
        val req = HttpRequestBuilder().apply {
            body = "<Foo>bar</Foo>".encodeToByteArray().source().toHttpBody()
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                10000000,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertTrue(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0).contentEquals(call.request.body.readAll()),
        )
    }

    @Test
    fun testCompressionWithMultipleHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers.append("Content-Encoding", "br")

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(listOf("br", "gzip"), call.request.headers.getAll("Content-Encoding"))
        assertTrue(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0).contentEquals(call.request.body.readAll()),
        )
    }
}
