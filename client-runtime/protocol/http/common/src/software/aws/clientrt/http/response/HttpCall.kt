/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.response

import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.time.Instant

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
     * TTFH as seen by the engine
     */
    val responseTime: Instant
)
