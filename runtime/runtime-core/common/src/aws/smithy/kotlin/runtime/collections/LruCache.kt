/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

public class LruCache<K, V>(
    public val maxSize: Int,
) {
    private val map = linkedMapOf<K, V>()

    public operator fun get(k: K): V? {
        return map[k].also { map.moveKeyToBack(k) }
    }

    public fun put(k: K, v: V): Unit {
        if (map.size == maxSize) {
            map.remove(map.entries.first().key)
        }
        map[k] = v
    }

    public fun remove(k: K): V? = map.remove(k)

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
