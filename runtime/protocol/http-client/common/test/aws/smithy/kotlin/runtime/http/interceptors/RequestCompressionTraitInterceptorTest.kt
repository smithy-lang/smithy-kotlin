/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.Gzip
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestCompressionTraitInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    @Test
    fun testNoCompressionBecauseOfThreshold() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionTraitInterceptor(
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
            RequestCompressionTraitInterceptor(
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
                RequestCompressionTraitInterceptor(
                    -1,
                    listOf("gzip"),
                    listOf(Gzip()),
                ),
            )
        }
    }

    @Ignore // TODO: Re-enable test
    @Test
    fun testCompression() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }

    @Ignore // TODO: Re-enable test
    @Test
    fun testStreamingPayloadAlwaysCompressed() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray()).toSdkByteReadChannel()!!.toHttpBody()
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                1000000000,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }

    @Ignore // TODO: Re-enable test
    @Test
    fun testCompressionWithMultipleHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers.append("Content-Encoding", "br")

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            ),
        )
        op.roundTrip(client, Unit)

        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(listOf("br", "gzip"), call.request.headers.getAll("Content-Encoding"))
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }
}
