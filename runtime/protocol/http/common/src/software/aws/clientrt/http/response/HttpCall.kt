/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.response

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A single request/response pair
 */
data class HttpCall(
    /**
     * The original complete request
     */
    val request: HttpRequest,

    /**
     * The [HttpResponse] for the given [request]
     */
    val response: HttpResponse,

    /**
     * The time the request was made by the engine
     */
    val requestTime: Instant,

    /**
     * The time the response was received. This is a rough estimate of Time-to-first-header (TTFH) as
     * reported by the engine.
     */
    val responseTime: Instant,

    /**
     * The context associated with this call
     */
    val callContext: CoroutineContext = EmptyCoroutineContext
)

/**
 * Close the underlying response and cleanup any resources associated with it.
 * After closing the response body is no longer valid and should not be read from.
 *
 * This must be called when finished with the response!
 */
internal fun HttpCall.complete() {
    val job = callContext[Job] as? CompletableJob ?: return
    job.complete()
}
