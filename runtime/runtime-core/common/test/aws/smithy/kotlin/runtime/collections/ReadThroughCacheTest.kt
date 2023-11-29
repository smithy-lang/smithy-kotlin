/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import aws.smithy.kotlin.runtime.time.ManualClock
import aws.smithy.kotlin.runtime.util.ExpiringValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ReadThroughCacheTest {
    @Test
    fun testReadThrough() = runTest {
        val clock = ManualClock()
        var counter = 0
        fun uncachedValue() = ExpiringValue(counter++, clock.now() + 2.seconds)
        val cache = ReadThroughCache<String, Int>(1.minutes, clock)

        // Basic read through
        assertEquals(0, cache.get("a") { uncachedValue() })
        assertEquals(1, cache.get("b") { uncachedValue() })

        // Basic cache verification
        assertEquals(0, cache.get("a") { uncachedValue() })
        assertEquals(1, cache.get("b") { uncachedValue() })

        // Expire the values in the cache
        clock.advance(3.seconds)

        // Expiry & fresh read through
        assertEquals(2, cache.get("a") { uncachedValue() })
        assertEquals(3, cache.get("b") { uncachedValue() })
    }

    @Test
    fun testSweep() = runTest {
        val clock = ManualClock()
        var counter = 0
        fun uncachedValue() = ExpiringValue(counter++, clock.now() + 2.seconds)
        val cache = ReadThroughCache<String, Int>(4.seconds, clock)

        // Pre-populate values
        assertEquals(0, cache.get("a") { uncachedValue() })
        assertEquals(1, cache.get("b") { uncachedValue() })
        assertEquals(2, cache.get("c") { uncachedValue() })

        // Sanity check
        assertEquals(3, cache.size)

        // Advance past expiration but don't read yet (thereby not instigating a sweep)
        clock.advance(3.seconds)
        assertEquals(3, cache.size)

        // Read a value but still no sweep
        assertEquals(3, cache.get("c") { uncachedValue() })
        assertEquals(3, cache.size)

        // Advance to the sweep point
        clock.advance(2.seconds)
        assertEquals(3, cache.size)
        assertEquals(4, cache.get("d") { uncachedValue() })
        assertEquals(1, cache.size)
    }
}
