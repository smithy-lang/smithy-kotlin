/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io.middleware

import software.aws.clientrt.io.Handler

/**
 * Implements [Handler] interface that transforms the request from [R1] to [R2]
 */
class MapRequest<R1, R2, Response, H>(
    private val inner: H,
    private val fn: suspend (R1) -> R2
) : Handler<R1, Response>
        where H : Handler<R2, Response> {
    override suspend fun call(request: R1): Response = inner.call(fn(request))
}
