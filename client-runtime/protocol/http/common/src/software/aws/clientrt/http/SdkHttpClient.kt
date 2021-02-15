/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.middleware.Service
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.OperationRequest
import software.aws.clientrt.http.response.HttpResponse

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
): Service<HttpRequestBuilder, HttpResponse> {

    init {
        // wire up the features
        config.install(this)

        // install ourselves into the engine
        engine.install(this)
    }

    override suspend fun call(request: HttpRequestBuilder): HttpResponse {
        return engine.roundTrip(request)
    }

    /**
     * Shutdown this HTTP client and close any resources. The client will no longer be capable of making requests.
     */
    fun close() {
        engine.close()
    }
}

// FIXME - how do we now signal that the HTTP response is done
// perhaps we should have `OperationResponse<T>(call: HttpCall, output: T) such that we can close the response

suspend inline fun<reified I, O> SdkHttpClient.roundTrip(
    context: ExecutionContext,
    service: Service<OperationRequest<I>, O>,
    input: I
): O {
    val opRequest = OperationRequest(context, input)
    return service.call(opRequest)
}
//
//suspend inline fun<reified I, O, R> SdkHttpClient.execute(
//    context: ExecutionContext,
//    service: Service<OperationRequest<I>, O>,
//    input: I,
//    crossinline block: suspend (O) -> R
//): R {
//    TODO()
////    val service = execution.decorate(this)
////    val opRequest = OperationRequest(context, input)
////    val output = service.call(opRequest)
////    return block(output)
//}
