/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MiddlewareTest {

    @Test
    fun testDecorate() = runTest {
        val handler = object : Handler<String, String> {
            override suspend fun call(request: String): String = request.replaceFirstChar { c -> c.uppercaseChar() }
        }

        val m1: MiddlewareFn<String, String> = { req, next ->
            next.call(req + "M1")
        }

        val m2: MiddlewareFn<String, String> = { req, next ->
            next.call(req + "M2")
        }

        val decorated = decorate(handler, MiddlewareLambda(m1), MiddlewareLambda(m2))

        val actual = decorated.call("foo")
        assertEquals("FooM1M2", actual)
    }

    @Test
    fun testServiceLambda() = runTest {
        val handler = HandlerLambda<String, String> {
            it.replaceFirstChar { c -> c.uppercaseChar() }
        }
        assertEquals("Foo", handler.call("foo"))
    }

    @Test
    fun testMapRequest() = runTest {
        val handler = HandlerLambda<String, String> {
            it
        }

        val mr = MapRequest(handler) { r1: Int ->
            r1.toString()
        }

        assertEquals("12", mr.call(12))
    }

    @Test
    fun testMapResponse() = runTest {
        val handler = HandlerLambda<String, String> {
            it
        }

        val mr = MapResponse(handler) { r: String ->
            r.toInt()
        }

        assertEquals(22, mr.call("22"))
    }
}

data class HandlerLambda<Request, Response>(private val fn: suspend (Request) -> Response) : Handler<Request, Response> {
    override suspend fun call(request: Request): Response = fn(request)
}

class MapRequest<R1, R2, Response, H>(
    private val inner: H,
    private val fn: suspend (R1) -> R2,
) : Handler<R1, Response> where H : Handler<R2, Response> {
    override suspend fun call(request: R1): Response = inner.call(fn(request))
}

class MapResponse<Request, R1, R2, H>(
    private val inner: H,
    private val fn: suspend (R1) -> R2,
) : Handler<Request, R2> where H : Handler<Request, R1> {
    override suspend fun call(request: Request): R2 {
        val res = inner.call(request)
        return fn(res)
    }
}
