/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.interceptors.AbstractChecksumInterceptor
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AbstractChecksumInterceptorTest {
    private val client = SdkHttpClient(TestEngine())
    private val CHECKSUM_TEST_HEADER = "x-amz-kotlin-sdk-test-checksum-header"

    @Test
    fun testChecksumIsCalculatedAndApplied() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("hello".encodeToByteArray())
        }
        val expectedChecksumValue = "abcd"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(TestAbstractChecksumInterceptor(expectedChecksumValue))

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expectedChecksumValue, call.request.headers[CHECKSUM_TEST_HEADER])
    }

    @Test
    fun testCachedChecksumIsUsed() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("hello".encodeToByteArray())
        }
        val expectedChecksumValue = "abcd"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(TestAbstractChecksumInterceptor(expectedChecksumValue))

        // the TestAbstractChecksumInterceptor will throw an exception if calculateChecksum is called more than once.
        op.roundTrip(client, Unit)
        op.roundTrip(client, Unit)
    }

    inner class TestAbstractChecksumInterceptor(
        private val expectedChecksum: String?,
    ) : AbstractChecksumInterceptor() {
        private var alreadyCalculatedChecksum = false

        override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
            check(!alreadyCalculatedChecksum) { "calculateChecksum was called more than once!" }
            return expectedChecksum.also { alreadyCalculatedChecksum = true }
        }

        override fun applyChecksum(
            context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
            checksum: String,
        ): HttpRequest {
            val req = context.protocolRequest.toBuilder()
            req.header(CHECKSUM_TEST_HEADER, checksum)
            return req.build()
        }
    }
}
