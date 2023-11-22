/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

/**
 * A collection similar to a [Map] except it allows multiple values to be associated with a single key. The associated
 * values are not necessarily distinct (e.g., key `foo` may be associated with value `bar` multiple times).
 * @param K The type of elements used as keys
 * @param V The type of elements used as values
 */
public interface MultiMap<K, V> : Map<K, List<V>> {
    /**
     * Checks if the specified [value] is present for the given [key]. Returns false if [key] is not present or if it
     * is not associated with [value].
     */
    public fun contains(key: K, value: V): Boolean = get(key)?.contains(value) ?: false

    /**
     * Gets a [Sequence] of key-value pairs. A given key will appear multiple times in the sequence if it is associated
     * with multiple values.
     */
    public val entryValues: Sequence<Map.Entry<K, V>>

    /**
     * Returns a mutable copy of this multimap. Changes to the returned mutable multimap do not affect this instance.
     */
    public fun toMutableMultiMap(): MutableMultiMap<K, V> =
        SimpleMutableMultiMap(mapValuesTo(mutableMapOf()) { (_, v) -> v.toMutableList() })
}

/**
 * Create a new [MultiMap] from the given key-value pairs
 * @param K The type of elements used as keys
 * @param V The type of elements used as values
 * @param pairs The elements to be contained by the new multimap
 */
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
