/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReadThroughCacheTest {
    @Test
    fun testReadThrough() = runTest {
        val clock = ManualClock()
        var counter = 0
        val cache = ReadThroughCache<String, Int>(1.minutes, clock) {
            ExpiringValue(counter++, clock.now() + 2.seconds)
        }

        // Basic read through
        assertEquals(0, cache.get("a"))
        assertEquals(1, cache.get("b"))

        // Basic cache verification
        assertEquals(0, cache.get("a"))
        assertEquals(1, cache.get("b"))

        // Expire the values in the cache
        clock.advance(3.seconds)

        // Expiry & fresh read through
        assertEquals(2, cache.get("a"))
        assertEquals(3, cache.get("b"))
    }

    @Test
    fun testSweep() = runTest {
        val clock = ManualClock()
        var counter = 0
        val cache = ReadThroughCache<String, Int>(4.seconds, clock) {
            ExpiringValue(counter++, clock.now() + 2.seconds)
        }

        // Pre-populate values
        assertEquals(0, cache.get("a"))
        assertEquals(1, cache.get("b"))
        assertEquals(2, cache.get("c"))

        // Sanity check
        assertEquals(3, cache.size)

        // Advance past expiration but don't read yet (thereby not instigating a sweep)
        clock.advance(3.seconds)
        assertEquals(3, cache.size)

        // Read a value but still no sweep
        assertEquals(3, cache.get("c"))
        assertEquals(3, cache.size)

        // Advance to the sweep point
        clock.advance(2.seconds)
        assertEquals(3, cache.size)
        assertEquals(4, cache.get("d"))
        assertEquals(1, cache.size)
    }
}
