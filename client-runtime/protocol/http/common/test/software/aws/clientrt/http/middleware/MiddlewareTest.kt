/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MiddlewareTest {

    @Test
    fun testDecorate() = runSuspendTest {
        val service = object : Service<String, String> {
            override suspend fun call(request: String): String {
                return request.capitalize()
            }
        }

        val m1: MiddlewareFn<String, String> = { req, next ->
            next.call(req + "M1")
        }

        val m2: MiddlewareFn<String, String> = { req, next ->
            next.call(req + "M2")
        }

        val decorated = decorate(service, MiddlewareLambda(m1), MiddlewareLambda(m2))

        val actual = decorated.call("foo")
        assertEquals("FooM1M2", actual)
    }

    @Test
    fun testServiceLambda() = runSuspendTest {
        val service = ServiceLambda<String, String> {
            it.capitalize()
        }
        assertEquals("Foo", service.call("foo"))
    }
}
