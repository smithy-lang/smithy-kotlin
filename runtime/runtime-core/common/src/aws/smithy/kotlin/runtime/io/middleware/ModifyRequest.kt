/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.Handler

/**
 * A transform that only modifies the input type
 */
@InternalApi
public interface ModifyRequest<Request> {
    public suspend fun modifyRequest(req: Request): Request
}

/**
 * Adapter for [ModifyRequest] to implement middleware
 */
internal class ModifyRequestMiddleware<Request, Response>(
    private val transform: ModifyRequest<Request>,
) : Middleware<Request, Response> {
    override suspend fun <H : Handler<Request, Response>> handle(request: Request, next: H): Response =
        next.call(transform.modifyRequest(request))
}
