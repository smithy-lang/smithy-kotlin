/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineClosedException
import aws.smithy.kotlin.runtime.http.engine.SdkRequestContextElement
import aws.smithy.kotlin.runtime.http.engine.createCallContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.io.Handler
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

typealias HttpHandler = Handler<HttpRequestBuilder, HttpCall>

/**
 * Create an [SdkHttpClient] with the given engine, and optionally configure it
 * This will **not** manage the engine lifetime, the caller is expected to close it.
 */
fun sdkHttpClient(
    engine: HttpClientEngine,
    manageEngine: Boolean = false,
): SdkHttpClient = SdkHttpClient(engine, manageEngine)

/**
 * An HTTP client capable of round tripping requests and responses
 *
 * **NOTE**: This is not a general purpose HTTP client. It is meant for generated SDK use.
 */
class SdkHttpClient(
    val engine: HttpClientEngine,
    private val manageEngine: Boolean = false
) : HttpHandler, Closeable {
    private val closed = atomic(false)

    suspend fun call(request: HttpRequest): HttpCall = executeWithCallContext(request)
    override suspend fun call(request: HttpRequestBuilder): HttpCall = executeWithCallContext(request.build())

    // FIXME - can we relocate to engine?
    private suspend fun executeWithCallContext(request: HttpRequest): HttpCall {
        if (!engine.coroutineContext.job.isActive) throw HttpClientEngineClosedException()
        val callContext = engine.createCallContext(coroutineContext)
        val context = callContext + SdkRequestContextElement(callContext)
        return withContext(context) {
            engine.roundTrip(request)
        }
    }

    /**
     * Shutdown this HTTP client and close any resources. The client will no longer be capable of making requests.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (manageEngine) {
            engine.close()
        }
    }
}
