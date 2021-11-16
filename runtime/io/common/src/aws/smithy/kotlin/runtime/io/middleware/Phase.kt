/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler

/**
 * A specific point in the lifecycle of executing a request where the input and output type(s)
 * are known / the same.
 *
 * There are many "steps" (phases) to executing an SDK operation (and HTTP requests by extension).
 * Giving these individual steps names and types allows for targeted application of middleware at
 * the (most) appropriate step.
 */
class Phase<Request, Response> : Middleware<Request, Response> {
    enum class Order {
        Before, After
    }

    private val middlewares = ArrayDeque<Middleware<Request, Response>>()

    /**
     * Insert [interceptor] in a specific order into the set of interceptors for this phase
     */
    fun intercept(order: Order = Order.After, interceptor: suspend (req: Request, next: Handler<Request, Response>) -> Response) {
        val wrapped = MiddlewareLambda(interceptor)
        register(wrapped, order)
    }

    /**
     * Insert a [transform] that only modifies the request of this phase
     */
    fun register(transform: ModifyRequest<Request>, order: Order = Order.After) {
        register(ModifyRequestMiddleware(transform), order)
    }

    /**
     * Insert a [transform] that only modifies the response of this phase
     */
    fun register(transform: ModifyResponse<Response>, order: Order = Order.After) {
        register(ModifyResponseMiddleware(transform), order)
    }

    /**
     * Register a middleware in a specific order
     */
    fun register(middleware: Middleware<Request, Response>, order: Order = Order.After) {
        when (order) {
            Order.Before -> middlewares.addFirst(middleware)
            Order.After -> middlewares.addLast(middleware)
        }
    }

    // runs all the registered interceptors for this phase
    override suspend fun <H : Handler<Request, Response>> handle(request: Request, next: H): Response {
        if (middlewares.isEmpty()) {
            return next.call(request)
        }

        val wrapped = decorate(next, *middlewares.toTypedArray())
        return wrapped.call(request)
    }
}
