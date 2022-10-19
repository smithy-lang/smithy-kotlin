/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineClosedException
import aws.smithy.kotlin.runtime.http.engine.SdkRequestContextElement
import aws.smithy.kotlin.runtime.http.engine.createCallContext
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.withRootTraceSpan
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

public typealias HttpHandler = Handler<SdkHttpRequest, HttpCall>

/**
 * Create an [SdkHttpClient] with the given engine, and optionally configure it
 * This will **not** manage the engine lifetime, the caller is expected to close it.
 */
public fun sdkHttpClient(
    engine: HttpClientEngine,
    manageEngine: Boolean = false,
): SdkHttpClient = SdkHttpClient(engine, manageEngine)

/**
 * An HTTP client capable of round tripping requests and responses
 *
 * **NOTE**: This is not a general purpose HTTP client. It is meant for generated SDK use.
 */
public class SdkHttpClient(
    public val engine: HttpClientEngine,
    private val manageEngine: Boolean = false,
) : HttpHandler, Closeable {
    private val closed = atomic(false)

    public suspend fun call(request: HttpRequest): HttpCall = coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
        executeWithCallContext(ExecutionContext(), request)
    }
    public suspend fun call(request: HttpRequestBuilder): HttpCall = call(request.build())
    override suspend fun call(request: SdkHttpRequest): HttpCall = executeWithCallContext(request.context, request.subject.build())

    // FIXME - can we relocate to engine?
    private suspend fun executeWithCallContext(context: ExecutionContext, request: HttpRequest): HttpCall {
        if (!engine.coroutineContext.job.isActive) throw HttpClientEngineClosedException()
        val callContext = engine.createCallContext(coroutineContext)
        val reqCoroutineContext = callContext + SdkRequestContextElement(callContext)
        return withContext(reqCoroutineContext) {
            engine.roundTrip(context, request)
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
