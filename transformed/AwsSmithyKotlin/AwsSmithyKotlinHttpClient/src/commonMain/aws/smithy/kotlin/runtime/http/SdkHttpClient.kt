/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.engine.*
import aws.smithy.kotlin.runtime.http.engine.SdkRequestContextElement
import aws.smithy.kotlin.runtime.http.engine.createCallContext
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

public typealias HttpHandler = Handler<SdkHttpRequest, HttpCall>

/**
 * An HTTP client capable of round tripping requests and responses
 *
 * **NOTE**: This is not a general purpose HTTP client. It is meant for generated SDK use.
 */
public class SdkHttpClient(
    public val engine: HttpClientEngine,
) : HttpHandler {
    public suspend fun call(request: HttpRequest): HttpCall = executeWithCallContext(ExecutionContext(), request)
    public suspend fun call(request: HttpRequestBuilder): HttpCall = call(request.build())
    override suspend fun call(request: SdkHttpRequest): HttpCall = executeWithCallContext(request.context, request.subject.build())

    private suspend fun executeWithCallContext(context: ExecutionContext, request: HttpRequest): HttpCall {
        if (!engine.coroutineContext.job.isActive) throw HttpClientEngineClosedException()
        val callContext = engine.createCallContext(coroutineContext)
        val reqCoroutineContext = callContext + SdkRequestContextElement(callContext)

        // STRUCTURED CONCURRENCY
        // The call context is used to drive an HTTP request to completion. This includes sending the request, receiving
        // the response headers, and finally processing the response body. The callContext is the parent of the
        // coroutine launched by async{}. This is because `roundTrip` returns after the response headers are available
        // and as such has a smaller scope than the overall call (which doesn't end until the response body is
        // consumed or the call is disposed of).
        return engine.async(reqCoroutineContext) {
            engine.roundTrip(context, request)
        }.await()
    }
}
