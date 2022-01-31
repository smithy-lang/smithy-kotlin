/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LazyAsyncValueTest {
    @Test
    fun testLoadOnce() = runTest {
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
