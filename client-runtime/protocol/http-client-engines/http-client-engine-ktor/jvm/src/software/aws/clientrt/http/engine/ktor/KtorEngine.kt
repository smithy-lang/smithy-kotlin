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
package software.aws.clientrt.http.engine.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse as SdkHttpResponse

/**
 * JVM [HttpClientEngine] backed by Ktor
 */
class KtorEngine(val config: HttpClientEngineConfig) : HttpClientEngine {
    val client: HttpClient

    init {
        client = HttpClient(OkHttp) {
            // TODO - propagate applicable client engine config to OkHttp engine
        }
    }

    override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): SdkHttpResponse {
        val builder = requestBuilder.toKtorRequestBuilder()
        // FIXME - this will not handle streaming bodies correctly. We will have to make a call based off
        // the expected response and figure out what to do from there. Likely wrapping the response in a coroutine
        // that reads from the underlying stream and forwards it to our own stream. Or always attempting to get a streaming
        // body and letting the downstream handlers make a judgement call on whether to consume it fully or continue in a streaming
        // fashion
        val response = client.request<HttpResponse>(builder)
        return response.toSdkHttpResponse(requestBuilder.build())
    }

    override fun close() {
        client.close()
    }
}
