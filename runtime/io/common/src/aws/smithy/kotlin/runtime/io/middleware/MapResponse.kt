/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler

/**
 * Implements [Handler] interface that transforms the response from [R1] to [R2]
 */
public class MapResponse<Request, R1, R2, H>(
    private val inner: H,
    private val fn: suspend (R1) -> R2,
) : Handler<Request, R2>
        where H : Handler<Request, R1> {
    override suspend fun call(request: Request): R2 {
        val res = inner.call(request)
        return fn(res)
    }
}
