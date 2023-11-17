/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

public interface MultiMap<K, V> : Map<K, List<V>> {
    public fun contains(key: K, value: V): Boolean = get(key)?.contains(value) ?: false

    public val entryValues: Sequence<Map.Entry<K, V>> // TODO Make more dynamic, e.g. Set<Map.Entry>

    public fun toMutableMultiMap(): MutableMultiMap<K, V> =
        SimpleMutableMultiMap(mapValuesTo(mutableMapOf()) { (_, v) -> v.toMutableList() })
}

public fun <K, V> multiMapOf(vararg pairs: Pair<K, V>): MultiMap<K, V> =
    SimpleMultiMap(pairs.groupBy(Pair<K, V>::first, Pair<K, V>::second))

internal class SimpleMultiMap<K, V>(
    private val delegate: Map<K, List<V>>,
) : MultiMap<K, V>, Map<K, List<V>> by delegate {
    override val entryValues: Sequence<Map.Entry<K, V>>
        get() = sequence {
            entries.forEach { (key, values) ->
                values.forEach { value -> yield(Entry(key, value)) }
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SimpleMultiMap<*, *>

        return delegate == other.delegate
    }

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
