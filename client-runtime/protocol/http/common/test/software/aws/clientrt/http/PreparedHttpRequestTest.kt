/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.request.PreparedHttpRequest
import software.aws.clientrt.http.request.ResponseTransformFailed
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.testing.runSuspendTest

class PreparedHttpRequestTest {

    @Test
    fun `it runs the pipelines`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
                val req = requestBuilder.build()
                val headers = Headers {}
                val body = HttpBody.Empty
                return HttpResponse(
                    HttpStatusCode.OK,
                    headers,
                    body,
                    req
                )
            }
        }

        val client = SdkHttpClient(
            mockEngine,
            HttpClientConfig()
        )

        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            context.headers.append("x-test", "testing")
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            proceedWith(context.response)
        }

        val preparedReq = PreparedHttpRequest(client, HttpRequestBuilder())
        val actual = preparedReq.receive<HttpResponse>()

        actual.request.headers.contains("x-test").shouldBeTrue()
    }

    @Test
    fun `it throws transform failed`(): Unit = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
                val req = requestBuilder.build()
                val headers = Headers {
                    append("x-foo", "bar")
                }
                val body = HttpBody.Empty
                return HttpResponse(
                    HttpStatusCode.OK,
                    headers,
                    body,
                    req
                )
            }
        }
        val client = SdkHttpClient(
            mockEngine,
            HttpClientConfig()
        )

        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            // makes output of type Int::class
            proceedWith(2)
        }

        val preparedReq = PreparedHttpRequest(client, HttpRequestBuilder())
        try {
            preparedReq.receive<HttpResponse>()
        } catch (ex: ResponseTransformFailed) {
            ex.message.shouldContain("Response transform failed: class kotlin.Int -> class")
            ex.message.shouldContain("200: OK")
            ex.message.shouldContain("x-foo: [bar]")
        }

        return@runSuspendTest Unit
    }
}
