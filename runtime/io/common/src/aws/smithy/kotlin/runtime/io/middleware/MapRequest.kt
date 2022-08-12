/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler

/**
 * Implements [Handler] interface that transforms the request from [R1] to [R2]
 */
public class MapRequest<R1, R2, Response, H>(
    private val inner: H,
    private val fn: suspend (R1) -> R2,
) : Handler<R1, Response>
        where H : Handler<R2, Response> {
    override suspend fun call(request: R1): Response = inner.call(fn(request))
}
