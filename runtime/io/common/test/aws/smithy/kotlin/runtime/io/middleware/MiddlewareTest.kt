/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io.middleware

import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.io.HandlerLambda
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
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
