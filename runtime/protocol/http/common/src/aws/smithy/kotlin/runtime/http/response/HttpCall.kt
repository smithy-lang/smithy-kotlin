/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A single request/response pair
 *
 * @param request the original complete request
 * @param response the [HttpResponse] for the given [request]
 * @param requestTime the time the request was made by the engine
 * @param responseTime the time the response was received. This is a rough estimate of Time-to-first-header (TTFH) as
 * reported by the engine.
 * @param coroutineContext the call context
 */
public open class HttpCall(
    public val request: HttpRequest,
    public val response: HttpResponse,
    public val requestTime: Instant = Instant.now(),
    public val responseTime: Instant = Instant.now(),
    public override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : CoroutineScope {

    @InternalApi
    public open fun copy(
        request: HttpRequest = this.request,
        response: HttpResponse = this.response,
    ): HttpCall = HttpCall(request, response, requestTime, responseTime, coroutineContext)

    /**
     * Hook to cancel an in-flight request
     */
    @InternalApi
    public open fun cancelInFlight() {
        runCatching {
            // ensure the response is cancelled
            (response.body as? HttpBody.ChannelContent)?.readFrom()?.cancel(null)
        }
    }
}

/**
 * Close the underlying response and cleanup any resources associated with it.
 * After closing the response body is no longer valid and should not be read from.
 *
 * This must be called when finished with the response!
 */
@InternalApi
public suspend fun HttpCall.complete() {
    val job = coroutineContext[Job] as? CompletableJob ?: return
    job.complete()
    if (!job.isCompleted) {
        // still outstanding children/work, the body may not have been consumed invoke the implementation specific
        // hook to cancel the in-flight call
        coroutineContext.logger<HttpCall>().trace { "cancelling in-flight call" }
        cancelInFlight()
    }
    job.join()
}
