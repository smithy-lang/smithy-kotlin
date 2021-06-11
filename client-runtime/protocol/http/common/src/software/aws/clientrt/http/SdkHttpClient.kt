/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.engine.HttpClientEngineClosedException
import software.aws.clientrt.http.engine.SdkRequestContextElement
import software.aws.clientrt.http.engine.createCallContext
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpCall
import software.aws.clientrt.io.Handler
import kotlin.coroutines.coroutineContext

typealias HttpHandler = Handler<HttpRequestBuilder, HttpCall>

/**
 * Create an [SdkHttpClient] with the given engine, and optionally configure it
 * This will **not** manage the engine lifetime, the caller is expected to close it.
 */
@HttpClientDsl
fun sdkHttpClient(
    engine: HttpClientEngine,
    configure: HttpClientConfig.() -> Unit = {},
    manageEngine: Boolean = false,
): SdkHttpClient {
    val config = HttpClientConfig().apply(configure)
    return SdkHttpClient(engine, config, manageEngine)
}

/**
 * An HTTP client capable of round tripping requests and responses
 *
 * **NOTE**: This is not a general purpose HTTP client. It is meant for generated SDK use.
 */
class SdkHttpClient(
    val engine: HttpClientEngine,
    val config: HttpClientConfig,
    private val manageEngine: Boolean = false
) : HttpHandler {
    private val closed = atomic(false)

    override suspend fun call(request: HttpRequestBuilder): HttpCall = executeWithCallContext(request)

    private suspend fun executeWithCallContext(request: HttpRequestBuilder): HttpCall {
        if (!engine.coroutineContext.job.isActive) throw HttpClientEngineClosedException()
        val callContext = engine.createCallContext(coroutineContext)
        val context = callContext + SdkRequestContextElement(callContext)
        return withContext(context) {
            engine.roundTrip(request.build())
        }
    }

    /**
     * Shutdown this HTTP client and close any resources. The client will no longer be capable of making requests.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (manageEngine) {
            engine.close()
        }
    }
}
