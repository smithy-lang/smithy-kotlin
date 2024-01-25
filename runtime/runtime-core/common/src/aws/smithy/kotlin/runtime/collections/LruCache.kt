/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe generic LRU (least recently used) cache.
 * Cache entries will be added up to a configured [capacity].
 * Once full, adding a new cache entry will evict the least recently used entry.
 */
public class LruCache<K, V>(
    public val capacity: Int,
) {
    private val mu = Mutex() // protects map
    private val map = linkedMapOf<K, V>()

    public suspend fun get(k: K): V? = withLock {
        getUnlocked(k)
    }

    public suspend fun getUnlocked(k: K): V? {
        map.moveKeyToBack(k)
        return map[k]
    }

    public suspend fun put(k: K, v: V): Unit = withLock {
        putUnlocked(k, v)
    }

    public suspend fun putUnlocked(k: K, v: V): Unit {
        if (map.size == capacity) {
            map.remove(map.entries.first().key)
        }
        map[k] = v
    }

    public suspend fun remove(k: K): V? = mu.withLock { map.remove(k) }

    public suspend fun <T> withLock(block: suspend () -> T): T = mu.withLock {
        block()
    }

    public val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.entries

    public val size: Int
        get() = map.size
}

private fun <K, V> LinkedHashMap<K, V>.moveKeyToBack(k: K) {
    if (containsKey(k)) {
        put(k, remove(k)!!)
    }
}
