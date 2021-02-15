/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

/**
 * Implements [Service] interface that transforms the response from [R1] to [R2]
 */
class MapResponse<Request, R1, R2, S>(
    private val inner: S,
    private val fn: suspend (R1) -> R2
) : Service<Request, R2>
        where S: Service<Request, R1>
{
    override suspend fun call(request: Request): R2 {
        val res = inner.call(request)
        return fn(res)
    }
}

