/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.request.PreparedHttpRequest
import software.aws.clientrt.http.request.ResponseTransformFailed
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline

class PreparedHttpRequestTest {

    @Test
    fun `it runs the pipelines`() = runBlocking {
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
        val actual = preparedReq.execute<HttpResponse>()

        actual.request.headers.contains("x-test").shouldBeTrue()
    }

    @Test
    fun `it throws transform failed`(): Unit = runBlocking {
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
            preparedReq.execute<HttpResponse>()
        } catch (ex: ResponseTransformFailed) {
            ex.message.shouldContain("Response transform failed: class kotlin.Int -> class")
            ex.message.shouldContain("200: OK")
            ex.message.shouldContain("x-foo: [bar]")
        }

        return@runBlocking Unit
    }
}
