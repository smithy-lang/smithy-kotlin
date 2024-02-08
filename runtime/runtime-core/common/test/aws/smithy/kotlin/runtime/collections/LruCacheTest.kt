/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
    fun testMutexLocks() = runTest {
        val cache = LruCache<String, String>(2)

        launch {
           cache.withLock {
               delay(5000L)
           }
        }

        launch {
            assertFails {
                withTimeout(1000L) {
                    cache.withLock {
                        println("Mom said it's my turn to use the cache!")
                    }
                }
            }
        }
    }
}
