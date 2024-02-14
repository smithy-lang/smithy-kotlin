/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Md5ChecksumInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    @Test
    fun itSetsContentMd5Header() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            Md5ChecksumInterceptor<Unit> {
                true
            },
        )

        val expected = "RG22oBSZFmabBbkzVGRi4w=="
        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expected, call.request.headers["Content-MD5"])
    }

    @Test
    fun itOnlySetsHeaderForBytesContent() = runTest {
        val req = HttpRequestBuilder().apply {
            body = object : HttpBody.ChannelContent() {
                override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel("fooey".encodeToByteArray())
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            Md5ChecksumInterceptor<Unit> {
                true
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertNull(call.request.headers["Content-MD5"])
    }

    @Test
    fun itDoesNotSetContentMd5Header() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            Md5ChecksumInterceptor<Unit> {
                false // interceptor disabled
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertNull(call.request.headers["Content-MD5"])
    }
}
