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

import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.request.PreparedHttpRequest
import software.aws.clientrt.http.response.HttpResponsePipeline

/**
 * Create an [SdkHttpClient] with the given engine, and optionally configure it
 */
@HttpClientDsl
fun sdkHttpClient(
    engine: HttpClientEngine,
    configure: HttpClientConfig.() -> Unit = {}
): SdkHttpClient {
    val config = HttpClientConfig().apply(configure)
    return SdkHttpClient(engine, config)
}

/**
 * An HTTP client capable of round tripping requests and responses
 *
 * **NOTE**: This is not a general purpose HTTP client. It is meant for generated SDK use.
 */
class SdkHttpClient(
    val engine: HttpClientEngine,
    val config: HttpClientConfig
) {

    /**
     * Request pipeline (middleware stack). Responsible for transforming inputs into an outgoing [HttpRequest]
     */
    val requestPipeline = HttpRequestPipeline()

    /**
     * Response pipeline. Responsible for transforming [HttpResponse] to the expected type
     */
    val responsePipeline = HttpResponsePipeline()

    init {
        // wire up the features
        config.install(this)

        // install ourselves into the engine
        engine.install(this)
    }

    /**
     * Shutdown this HTTP client and close any resources. The client will no longer be capable of making requests.
     */
    fun close() {
        engine.close()
    }
}

/**
 * Make an HTTP request with the given input type. The input type is expected to be transformable by the request
 * pipeline. The output type [TResponse] is expected to be producible by the response pipeline.
 */
suspend inline fun <reified TResponse> SdkHttpClient.roundTrip(input: Any, responseContext: Any? = null): TResponse =
    PreparedHttpRequest(this, HttpRequestBuilder(), input, responseContext).receive()

/**
 * Make an HTTP request using the given [HttpRequestBuilder]. The body of the request builder will be used as the
 * subject of the request pipeline.
 */
suspend inline fun <reified TResponse> SdkHttpClient.roundTrip(builder: HttpRequestBuilder, responseContext: Any? = null): TResponse =
    PreparedHttpRequest(this, builder, userContext = responseContext).receive()

/**
 * Make an HTTP request with the given input type and run the [block] with the result of the response pipeline.
 *
 * The underlying HTTP response will remain available until the block returns making this method suitable for
 * streaming responses.
 */
suspend inline fun <reified TResponse, R> SdkHttpClient.execute(
    input: Any,
    responseContext: Any?,
    crossinline block: suspend (TResponse) -> R
): R =
    PreparedHttpRequest(this, HttpRequestBuilder(), input, responseContext).execute(block)
