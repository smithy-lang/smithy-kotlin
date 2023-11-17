/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlin.jvm.JvmName

// TODO can this inherit from MultiMap?
public interface MutableMultiMap<K, V> : MutableMap<K, MutableList<V>> {
    public fun add(key: K, value: V): Boolean
    public fun add(key: K, index: Int, value: V)
    public fun addAll(key: K, values: Collection<V>): Boolean
    public fun addAll(key: K, index: Int, values: Collection<V>): Boolean

    public fun addAll(other: Map<K, List<V>>) {
        other.entries.forEach { (key, values) -> addAll(key, values) }
    }

    public fun contains(key: K, value: V): Boolean = get(key)?.contains(value) ?: false
    public val entryValues: Sequence<Map.Entry<K, V>> // TODO Make more dynamic, e.g. Set<MutableMap.MutableEntry>

    public fun put(key: K, value: V): MutableList<V>? = put(key, mutableListOf(value))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeElement")
    public fun remove(key: K, value: V): Boolean

    public fun removeAt(key: K, index: Int): V?
    public fun removeAll(key: K, values: Collection<V>): Boolean?
    public fun retainAll(key: K, values: Collection<V>): Boolean?
    public fun toMultiMap(): MultiMap<K, V> = SimpleMultiMap(mapValues { (_, v) -> v.toList() }.toMap())
}

public fun <K, V> mutableMultiMapOf(vararg pairs: Pair<K, V>): MutableMultiMap<K, V> =
    SimpleMutableMultiMap(pairs.groupByTo(mutableMapOf(), Pair<K, V>::first, Pair<K, V>::second))

internal class SimpleMutableMultiMap<K, V>(
    private val delegate: MutableMap<K, MutableList<V>>,
) : MutableMap<K, MutableList<V>> by delegate, MutableMultiMap<K, V> {
    private fun ensureKey(key: K) = getOrPut(key, ::mutableListOf)

    override fun add(key: K, value: V): Boolean = ensureKey(key).add(value)

    override fun add(key: K, index: Int, value: V) {
        ensureKey(key).add(index, value)
    }

    override fun addAll(key: K, values: Collection<V>): Boolean = ensureKey(key).addAll(values)

    override fun addAll(key: K, index: Int, values: Collection<V>): Boolean = ensureKey(key).addAll(index, values)

    override val entryValues: Sequence<Map.Entry<K, V>>
        get() = sequence {
            entries.forEach { (key, values) ->
                values.forEach { value -> yield(Entry(key, value)) }
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SimpleMutableMultiMap<*, *>

        return delegate == other.delegate
    }

    override fun hashCode(): Int = delegate.hashCode()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeElement")
    override fun remove(key: K, value: V): Boolean = this[key]?.remove(value) ?: false

    override fun removeAt(key: K, index: Int): V? = get(key)?.removeAt(index)

    override fun removeAll(key: K, values: Collection<V>): Boolean? = get(key)?.removeAll(values)

    override fun retainAll(key: K, values: Collection<V>): Boolean? = get(key)?.retainAll(values)

    override fun toString(): String = delegate.toString()
}
