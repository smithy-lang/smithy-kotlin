/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

/**
 * A specific point in the lifecycle of executing a request where the input and output type(s)
 * are known / the same.
 *
 * There are many "steps" (phases) to executing an SDK operation (and HTTP requests by extension).
 * Giving these individual steps names and types allows for targeted application of middleware at
 * the (most) appropriate step.
 */
class Phase<Request, Response>: Middleware<Request, Response> {
    enum class Order {
        Before, After
    }

    private val middlewares = mutableListOf<Middleware<Request, Response>>()

    /**
     * Append [interceptor] to the set of interceptors for this phase
     */
    fun intercept(interceptor: suspend (req: Request, next: Service<Request, Response>) -> Response) {
        intercept(Order.After, interceptor)
    }

    /**
     * Insert [interceptor] in a specific order
     */
    fun intercept(order: Order, interceptor: suspend (req: Request, next: Service<Request, Response>) -> Response) {
        val wrapped = MiddlewareLambda(interceptor)
        register(order, wrapped)
    }

    /**
     * Register a middleware in a specific order
     */
    fun register(order: Order, middleware: Middleware<Request, Response>) {
        when(order) {
            Order.Before -> middlewares.add(0, middleware)
            Order.After -> middlewares.add(middleware)
        }
    }

    // runs all the registered interceptors for this phase
    override suspend fun <S : Service<Request, Response>> handle(request: Request, next: S): Response {
        if (middlewares.isEmpty()) {
            return next.call(request)
        }

        val wrapped = decorate(next, *middlewares.toTypedArray())
        return wrapped.call(request)
    }
}
