/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LazyAsyncValueTest {
    @Test
    fun testLoadOnce() = runSuspendTest {
        var calls = 0
        val initializer = suspend {
            calls++
        }

        val value = asyncLazy(initializer)

        repeat(5) {
            value.get()
        }

        assertEquals(1, calls)
    }
}
