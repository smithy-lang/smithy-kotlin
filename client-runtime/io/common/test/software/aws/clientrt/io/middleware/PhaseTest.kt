/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io.middleware

import software.aws.clientrt.io.Handler
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PhaseTest {
    @Test
    fun `it orders interceptors`() = runSuspendTest {
        val phase = Phase<String, String>()
        val order = mutableListOf<String>()

        phase.intercept { req, next ->
            order.add("first")
            next.call(req)
        }

        phase.intercept(Phase.Order.Before) { req, next ->
            order.add("second")
            next.call(req)
        }

        phase.intercept(Phase.Order.After) { req, next ->
            order.add("third")
            next.call(req)
        }

        val handler = object : Handler<String, String> {
            override suspend fun call(request: String): String {
                return request.capitalize()
            }
        }

        val actual = phase.handle("foo", handler)
        assertEquals("Foo", actual)
        assertEquals(listOf("second", "first", "third"), order)
    }
}
