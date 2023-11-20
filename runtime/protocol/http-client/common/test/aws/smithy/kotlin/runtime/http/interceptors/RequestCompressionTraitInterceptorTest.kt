/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.Gzip
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.toByteStream
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
    fun testNoCompression() = runTest {
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
        assertFailsWith<IllegalArgumentException> {
            val req = HttpRequestBuilder().apply {
                body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
            }

            val op = newTestOperation<Unit, Unit>(req, Unit)
            op.interceptors.add(
                RequestCompressionTraitInterceptor(
                    -1,
                    listOf("gzip"),
                    listOf(Gzip()),
                ),
            )
            op.roundTrip(client, Unit)
        }
    }

    @Ignore
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

    @Ignore
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
        assertEquals("br, gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toByteStream()!!.toByteArray().decodeToString())
    }
}
