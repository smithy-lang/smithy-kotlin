/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.IdempotencyTokenProvider
import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.ExecutionContext
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.http.sdkHttpClient
import software.aws.clientrt.serde.json.JsonSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerdeProvider
import software.aws.clientrt.testing.runSuspendTest

class HttpSerdeTest {
    @Test
    fun `it serializes JSON`() = runSuspendTest {
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
        val subject = object : HttpSerialize {
            override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
                builder.headers.append("called", "true")
            }
        }
        client.requestPipeline.execute(builder, subject)
        assertEquals("true", builder.headers["called"])
    }

    @Test
    fun `it serializes XML`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(HttpSerde) {
                serdeProvider = XmlSerdeProvider()
                idempotencyTokenProvider = IdempotencyTokenProvider.Default
            }
        }

        val builder = HttpRequestBuilder()
        val subject = object : HttpSerialize {
            override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
                builder.headers.append("called", "true")
            }
        }
        client.requestPipeline.execute(builder, subject)
        assertEquals("true", builder.headers["called"])
    }

    @Test
    fun `it deserializes JSON`() = runSuspendTest {
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
            deserializer = userDeserializer
        }

        val req = HttpRequestBuilder().build()
        val httpResp = HttpResponse(HttpStatusCode.OK, Headers {}, HttpBody.Empty, req)
        val context = HttpResponseContext(httpResp, TypeInfo(Int::class), execCtx)

        val actual = client.responsePipeline.execute(context, httpResp.body)
        assertEquals(2, actual)
    }

    @Test
    fun `it deserializes XML`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(HttpSerde) {
                serdeProvider = XmlSerdeProvider()
                idempotencyTokenProvider = IdempotencyTokenProvider.Default
            }
        }

        val userDeserializer = object : HttpDeserialize {
            override suspend fun deserialize(response: HttpResponse, provider: DeserializationProvider): Any {
                return 2
            }
        }

        val req = HttpRequestBuilder().build()
        val httpResp = HttpResponse(HttpStatusCode.OK, Headers {}, HttpBody.Empty, req)
        val execCtx = ExecutionContext.build {
            deserializer = userDeserializer
        }
        val context = HttpResponseContext(httpResp, TypeInfo(Int::class), execCtx)

        val actual = client.responsePipeline.execute(context, httpResp.body)
        assertEquals(2, actual)
    }
}
