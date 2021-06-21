/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler

/**
 * Decorates a [Handler], transforming either the request or the response
 */
interface Middleware<Request, Response> {
    suspend fun <H> handle(request: Request, next: H): Response
            where H : Handler<Request, Response>
}

/**
 * Alias for a lambda based [Middleware]
 */
typealias MiddlewareFn<Request, Response> = suspend (Request, Handler<Request, Response>) -> Response

/**
 * Adapter for [MiddlewareFn] that implements [Middleware]
 */
data class MiddlewareLambda<Request, Response>(
    private val fn: MiddlewareFn<Request, Response>
) : Middleware<Request, Response> {
    override suspend fun <H : Handler<Request, Response>> handle(request: Request, next: H): Response =
        fn(request, next)
}

/**
 * Service decorated with middleware
 */
private data class DecoratedHandler<Request, Response>(
    val handler: Handler<Request, Response>,
    val with: Middleware<Request, Response>
) : Handler<Request, Response> {
    override suspend fun call(request: Request): Response = with.handle(request, handler)
}

/**
 * decorate [handler] with the given [middleware] returning a new wrapped service
 */
fun <Request, Response> decorate(
    handler: Handler<Request, Response>,
    vararg middleware: Middleware<Request, Response>
): Handler<Request, Response> {
    if (middleware.isEmpty()) return handler
    return middleware.dropLast(1).foldRight(DecoratedHandler(handler, middleware.last())) { m, h ->
        DecoratedHandler(handler = h, with = m)
    }
}
