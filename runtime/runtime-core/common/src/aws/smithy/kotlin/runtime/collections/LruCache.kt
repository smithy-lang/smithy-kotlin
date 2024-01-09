/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe generic LRU (least recently used) cache.
 * Cache entries will be added up to a configured [maxSize].
 * Once full, adding a new cache entry will evict the least recently used entry.
 */
public class LruCache<K, V>(
    public val maxSize: Int,
) {
    private val mu = Mutex() // protects map
    private val map = linkedMapOf<K, V>()

    public suspend fun get(k: K): V? = mu.withLock {
        return map[k].also { map.moveKeyToBack(k) }
    }

    public suspend fun put(k: K, v: V): Unit = mu.withLock {
        if (map.size == maxSize) {
            map.remove(map.entries.first().key)
        }
        map[k] = v
    }

    public suspend fun remove(k: K): V? = mu.withLock { map.remove(k) }

    public val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.entries

    public val size: Int
        get() = map.size
}

private fun <K, V> LinkedHashMap<K, V>.moveKeyToBack(k: K): Unit {
    if (containsKey(k)) {
        put(k, remove(k)!!)
    }
}
