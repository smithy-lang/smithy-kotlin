/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class LruCacheTest {
    @Test
    fun testGetAndPut() = runTest {
        val cache = LruCache<String, String>(1)
        cache.put("a", "1")
        assertEquals(cache.get("a"), "1")
    }

    @Test
    fun testGetShouldUpdateLruStatus() = runTest {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        assertEquals(3, cache.size)
        assertEquals("a, b, c", cache.entries.joinToString { it.key })

        cache.get("a")
        assertEquals("b, c, a", cache.entries.joinToString { it.key })
    }

    @Test
    fun testPutShouldUpdateLruStatus() = runTest {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        assertEquals(3, cache.size)
        assertEquals("a, b, c", cache.entries.joinToString { it.key })

        cache.put("a", "4")
        assertEquals("b, c, a", cache.entries.joinToString { it.key })
    }

    @Test
    fun testEviction() = runTest {
        val cache = LruCache<String, String>(2)
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals(2, cache.size)

        cache.put("c", "3")
        assertEquals(2, cache.size)
        assertEquals("b, c", cache.entries.joinToString { it.key })
    }

    @Test
    fun testUpdatingKeyWhenCacheIsFullDoesNotEvict() = runTest {
        val cache = LruCache<String, Int>(2)

        cache.put("a", 1)

        cache.put("b", 2)
        cache.put("b", 3)

        assertEquals(2, cache.size)
    }

    @Test
    fun testCapacity() = runTest {
        assertFailsWith<IllegalArgumentException> {
            LruCache<Any, Any>(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            LruCache<Any, Any>(0)
        }
        LruCache<Any, Any>(1)
    }
}
