/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.config.IdempotencyTokenProvider
import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestContext
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.serde.json.JsonSerdeProvider
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSerdeTest {
    @Test
    fun itSerializes() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(HttpSerde) {
                serdeProvider = JsonSerdeProvider()
                idempotencyTokenProvider = IdempotencyTokenProvider.Default
            }
        }

        val builder = HttpRequestBuilder()
        val input = object : HttpSerialize {
            override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
                builder.headers.append("called", "true")
            }
        }
        val ctx = HttpRequestContext(
            ExecutionContext.build {
                attributes[SdkHttpOperation.OperationSerializer] = input
            }
        )
        client.requestPipeline.execute(ctx, builder)
        assertEquals("true", builder.headers["called"])
    }

    @Test
    fun itDeserializes() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(HttpSerde) {
                serdeProvider = JsonSerdeProvider()
                idempotencyTokenProvider = IdempotencyTokenProvider.Default
            }
        }

        val userDeserializer = object : HttpDeserialize {
            override suspend fun deserialize(response: HttpResponse, provider: DeserializationProvider): Any {
                return 2
            }
        }

        val execCtx = ExecutionContext.build {
            attributes[SdkHttpOperation.OperationDeserializer] = userDeserializer
        }

        val req = HttpRequestBuilder().build()
        val httpResp = HttpResponse(HttpStatusCode.OK, Headers {}, HttpBody.Empty, req)
        val context = HttpResponseContext(httpResp, TypeInfo(Int::class), execCtx)

        val actual = client.responsePipeline.execute(context, httpResp.body)
        assertEquals(2, actual)
    }
}
