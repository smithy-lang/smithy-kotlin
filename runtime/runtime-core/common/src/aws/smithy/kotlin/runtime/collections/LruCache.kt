/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe generic LRU (least recently used) cache.
 * Entries will be added up to a configured [capacity].
 * Once full, adding a new entry will evict the least recently used entry.
 */
public class LruCache<K, V>(
    public val capacity: Int,
) {
    private val mu = Mutex() // protects map
    private val map = linkedMapOf<K, V>()

    /**
     * Returns the value for a key [k], or null if it does not exist.
     * @param k the key to look up
     * @return the value associated with the key, or null if it does not exist
     */
    public suspend fun get(k: K): V? = withLock {
        getUnlocked(k)
    }

    /**
     * Returns the value for a key [k], or null if it does not exist.
     * Note: This method is not thread-safe! The mutex must be locked before calling.
     * @param k the key to look up
     * @return the value associated with the key, or null if it does not exist
     */
    public suspend fun getUnlocked(k: K): V? {
        map.moveKeyToBack(k)
        return map[k]
    }

    /**
     * Add a new entry to the cache with a key [k] and value [v].
     * @param k the key to associate the value with
     * @param v the value to store in the cache
     * @return [Unit]
     */
    public suspend fun put(k: K, v: V): Unit = withLock {
        putUnlocked(k, v)
    }

    /**
     * Add a new entry to the cache with a key [k] and value [v].
     * Note: This method is not thread-safe! The mutex must be locked before calling.
     * @param k the key to associate the value with
     * @param v the value to store in the cache
     * @return [Unit]
     */
    public suspend fun putUnlocked(k: K, v: V): Unit {
        if (map.size == capacity) {
            map.remove(map.entries.first().key)
        }
        map[k] = v
    }

    /**
     * Remove an entry associated with a key [k], if it exists.
     * @param k the key to remove from the cache
     * @return the value removed, or null if it did not exist
     */
    public suspend fun remove(k: K): V? = mu.withLock { map.remove(k) }

    /**
     * Lock the mutex and execute a function [block].
     * Note: It is safe for [block] to call [getUnlocked] and [putUnlocked].
     * @param block A function accepting zero arguments and returning some value [T]
     * @return The result of the function, [T]
     */
    public suspend fun <T> withLock(block: suspend () -> T): T = mu.withLock {
        block()
    }

    /**
     * Get the set of entries in the cache.
     * Note: This is not thread safe! If you plan to iterate over entries, lock the mutex.
     */
    public val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.entries

    /**
     * Get the current size of the cache
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
