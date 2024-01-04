/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals


public class LruCacheTest {
    @Test
    fun testGetAndPut() = runTest {
        val cache = LruCache<String, String>(1)
        cache.put("a", "a")
        assertEquals(cache["a"], "a")
    }

    @Test
    fun testGetShouldMoveToBack() = runTest {
        val cache = LruCache<String, String>(3)
        cache.put("a", "a")
        cache.put("b", "b")
        cache.put("c", "c")

        assertEquals(3, cache.size)
        assertEquals("a, b, c", cache.entries.joinToString { it.key })

        cache.get("a")
        assertEquals("b, c, a", cache.entries.joinToString { it.key })
    }

    @Test
    fun testEviction() = runTest {
        val cache = LruCache<String, String>(2)
        cache.put("a", "a")
        cache.put("b", "b")
        assertEquals(2, cache.size)

        cache.put("c", "c")
        assertEquals(2, cache.size)
        assertEquals("b, c", cache.entries.joinToString { it.key })
    }
}