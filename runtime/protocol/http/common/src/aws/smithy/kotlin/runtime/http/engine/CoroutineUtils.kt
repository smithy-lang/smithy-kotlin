/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Create a [CoroutineContext] (Job) for executing a single request/response (HttpCall)
 * @param outerContext The context from the outer scope
 */
internal fun HttpClientEngine.createCallContext(outerContext: CoroutineContext): CoroutineContext {
    // attach request to engine (will ensure that shutdown only is invoked after all in-flight requests complete)
    val requestJob = Job(coroutineContext[Job])
    val reqContext = coroutineContext + requestJob + CoroutineName("request-context")

    // attach req to outer context, if the user cancels it will be propagated to the request
    attachToOuterJob(outerContext, requestJob)
    return reqContext
}

/**
 * Attach [requestJob] to the current [outerContext] job if it exists such that if the parent
 * completes with an exception then the request job does too
 */
@OptIn(InternalCoroutinesApi::class)
private fun attachToOuterJob(outerContext: CoroutineContext, requestJob: Job) {
    val parentJob = outerContext[Job] ?: return
    val cleanupHandler = parentJob.invokeOnCompletion(onCancelling = true) { cause ->
        cause ?: return@invokeOnCompletion
        requestJob.cancel(CancellationException(cause.message, cause))
    }

    requestJob.invokeOnCompletion {
        cleanupHandler.dispose()
    }
}

/**
 * Pull the context that has the associated [Job] for the request out of the context.
 * [HttpClientEngine] implementations *MUST* use this as the context for [aws.smithy.kotlin.runtime.http.response.HttpCall.callContext]
 * Any request scoped resources or cleanup should be tied to the [Job] instance of this context.
 *
 * e.g.
 *
 * ```
 * class MyEngine : HttpClientEngineBase("my-engine") {
 *     fun roundTrip(request: HttpRequest): HttpCall {
 *         val callContext = callContext()
 *
 *         val resp = ...
 *
 *         callContext[Job]?.invokeOnCompletion { cause ->
 *            releaseResponse(resp)
 *         }
 *
 *         return HttpCall(req, resp, ..., callContext)
 *     }
 * }
 * ```
 */
@InternalApi
public suspend fun callContext(): CoroutineContext = coroutineContext[SdkRequestContextElement]!!.callContext

/**
 * Stores the request context
 */
internal class SdkRequestContextElement(val callContext: CoroutineContext) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = SdkRequestContextElement

    public companion object : CoroutineContext.Key<SdkRequestContextElement>
}
