/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol.rpcv2.cbor

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal data class TestOutput(val body: HttpBody)

internal inline fun <reified I> newTestOperation(serialized: HttpRequestBuilder): SdkHttpOperation<I, TestOutput> =
    SdkHttpOperation.build<I, TestOutput> {
        serializeWith = object : HttpSerializer.NonStreaming<I> {
            override fun serialize(context: ExecutionContext, input: I): HttpRequestBuilder = serialized
        }

        deserializeWith = object : HttpDeserializer.Streaming<TestOutput> {
            override suspend fun deserialize(context: ExecutionContext, call: HttpCall): TestOutput = TestOutput(call.response.body)
        }

        context {
            // required operation context
            operationName = "TestOperation"
            serviceName = "TestService"
        }
    }

internal fun getMockClient(response: ByteArray, responseHeaders: Headers = Headers.Empty): SdkHttpClient {
    val mockEngine = TestEngine { _, request ->
        val body = object : HttpBody.SourceContent() {
            override val contentLength: Long = response.size.toLong()
            override fun readFrom(): SdkSource = response.source()
            override val isOneShot: Boolean get() = false
        }

        val resp = HttpResponse(HttpStatusCode.OK, responseHeaders, body)

        HttpCall(request, resp, Instant.now(), Instant.now())
    }
    return SdkHttpClient(mockEngine)
}

internal val RESPONSE = "abc".repeat(1024).encodeToByteArray()


class RpcV2CborSmithyProtocolResponseHeaderInterceptorTest  {
    @Test
    fun testThrowsOnMissingHeader() = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit>(req)

        op.interceptors.add(RpcV2CborSmithyProtocolResponseHeaderInterceptor)

        val client = getMockClient(response = RESPONSE, responseHeaders = Headers.Empty)

        assertFailsWith<ClientException> {
            op.roundTrip(client, Unit)
        }
    }

    @Test
    fun testSucceedsOnPresentHeader() = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit>(req)

        op.interceptors.add(RpcV2CborSmithyProtocolResponseHeaderInterceptor)

        val responseHeaders = HeadersBuilder().apply {
            append("smithy-protocol", "rpc-v2-cbor")
        }.build()

        val client = getMockClient(response = RESPONSE, responseHeaders)
        op.roundTrip(client, Unit)
    }
}