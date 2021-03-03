/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io.middleware

import software.aws.clientrt.io.Service

/**
 * Decorates a [Service], transforming either the request or the response
 */
interface Middleware<Request, Response> {
    suspend fun <S> handle(request: Request, next: S): Response
            where S : Service<Request, Response>
}

/**
 * Alias for a lambda based [Middleware]
 */
typealias MiddlewareFn<Request, Response> = suspend (Request, Service<Request, Response>) -> Response

/**
 * Adapter for [MiddlewareFn] that implements [Middleware]
 */
data class MiddlewareLambda<Request, Response>(
    private val fn: MiddlewareFn<Request, Response>
) : Middleware<Request, Response> {
    override suspend fun <S : Service<Request, Response>> handle(request: Request, next: S): Response {
        return fn(request, next)
    }
}

/**
 * Service decorated with middleware
 */
private data class DecoratedService<Request, Response>(
    val service: Service<Request, Response>,
    val with: Middleware<Request, Response>
) : Service<Request, Response> {

    override suspend fun call(request: Request): Response {
        return with.handle(request, service)
    }
}

/**
 * decorate [service] with the given [middleware] returning a new wrapped service
 */
fun <Request, Response> decorate(
    service: Service<Request, Response>,
    vararg middleware: Middleware<Request, Response>
): Service<Request, Response> {
    if (middleware.isEmpty()) return service
    return middleware.dropLast(1).foldRight(DecoratedService(service, middleware.last())) { m, s ->
        DecoratedService(service = s, with = m)
    }
}
