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
import aws.smithy.kotlin.runtime.http.response.HttpCall
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

    // FIXME - can we relocate to engine?
    private suspend fun executeWithCallContext(context: ExecutionContext, request: HttpRequest): HttpCall {
        if (!engine.coroutineContext.job.isActive) throw HttpClientEngineClosedException()
        val callContext = engine.createCallContext(coroutineContext)
        val reqCoroutineContext = callContext + SdkRequestContextElement(callContext)
        return withContext(reqCoroutineContext) {
            engine.roundTrip(context, request)
        }
    }
}
