/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

/**
 * Implements [Service] interface that transforms the request from [R1] to [R2]
 */
class MapRequest<R1, R2, Response, S>(
    private val inner: S,
    private val fn: suspend (R1) -> R2
) : Service<R1, Response>
        where S: Service<R2, Response>
{
    override suspend fun call(request: R1): Response {
        return inner.call(fn(request))
    }
}

