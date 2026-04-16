/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe generic LRU (least recently used) cache.
 * Entries will be added up to a configured [capacity].
 * Once full, adding a new entry will evict the least recently used entry.
 */
@InternalApi
public class LruCache<K, V>(
    public val capacity: Int,
) {
    private val mu = Mutex() // protects map
    private val map = linkedMapOf<K, V>()

    init {
        require(capacity > 0) { "cache capacity must be greater than 0, was $capacity" }
    }

    /**
     * Returns the value for a key [k], or null if it does not exist.
     * @param k the key to look up
     * @return the value associated with the key, or null if it does not exist
     */
    public suspend fun get(k: K): V? = mu.withLock {
        map.moveKeyToBack(k)
        return map[k]
    }

    /**
     * Add or update a cache entry with a key [k] and value [v].
     * @param k the key to associate the value with
     * @param v the value to store in the cache
     * @return [Unit]
     */
    public suspend fun put(k: K, v: V): Unit = mu.withLock {
        if (k !in map && map.size == capacity) {
            map.remove(map.keys.first())
        }
        map[k] = v
        map.moveKeyToBack(k)
    }

    /**
     * Remove an entry associated with a key [k], if it exists.
     * @param k the key to remove from the cache
     * @return the value removed, or null if it did not exist
     */
    public suspend fun remove(k: K): V? = mu.withLock { map.remove(k) }

    /**
     * Get a snapshot of the entries in the cache.
     * Note: This is not thread-safe! the underlying entries may change immediately after calling.
     */
    public val entries: Set<Map.Entry<K, V>>
        get() = map.toMap().entries

    /**
     * Get the current size of the cache
     * Note: This is not thread-safe! The size may change immediately after calling.
     */
    public val size: Int
        get() = map.size
}

// Move a key [k] to the back of the map (indicating it is most recently used)
private fun <K, V> LinkedHashMap<K, V>.moveKeyToBack(k: K) {
    if (containsKey(k)) {
        put(k, remove(k)!!)
    }
}
