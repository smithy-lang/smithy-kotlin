/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlin.jvm.JvmName

/**
 * A mutable collection similar to a [MutableMap] except it allows multiple values to be associated with a single key.
 * The associated values are not necessarily distinct (e.g., key `foo` may be associated with value `bar` multiple
 * times).
 * @param K The type of elements used as keys
 * @param V The type of elements used as values
 */
public interface MutableMultiMap<K, V> : MutableMap<K, MutableList<V>> {
    /**
     * Adds an association from the given [key] to the given [value]
     * @param key The key to associate
     * @param value The value to associate
     * @return True because the multimap is always modified as the result of this operation
     */
    public fun add(key: K, value: V): Boolean

    /**
     * Adds an association from the given [key] to the given [value], inserting it at the given [index] in the list of
     * values already associated with the [key].
     * @param key The key to associate
     * @param index The index at which to insert [value] in the list of associated values
     * @param value The value to associate
     */
    public fun add(key: K, index: Int, value: V)

    /**
     * Adds associations from the given [key] to the given [values]. This will _append_ to the existing associations,
     * not merge or deduplicate. This operation copies from the given [values]. Later changes to the collection do not
     * affect this instance.
     * @param key The key to associate
     * @param values The values to associate
     * @return True because the multimap is always modified as the result of this operation
     */
    public fun addAll(key: K, values: Collection<V>): Boolean

    /**
     * Adds associations from the given [key] to the given [values], inserting them at the given [index] in the list of
     * values already associated with the [key]. This operation copies from the given [values]. Later changes to the
     * collection do not affect this instance.
     * @param key The key to associate
     * @param index The index at which to insert [values] in the list of associated values
     * @param values The values to associate
     * @return True because the multimap is always modified as the result of this operation
     */
    public fun addAll(key: K, index: Int, values: Collection<V>): Boolean

    /**
     * Adds all the key-value associations from another map into this one. This will _append_ to the existing
     * associations, not merge or deduplicate. This operation copies from the given values lists. Later changes to those
     * lists do not affect this collection.
     * @param other The other map from which to copy values
     */
    public fun addAll(other: Map<K, List<V>>) {
        other.entries.forEach { (key, values) -> addAll(key, values) }
    }

    /**
     * Checks if the specified [value] is present for the given [key]. Returns false if [key] is not present or if it
     * is not associated with [value].
     */
    public fun contains(key: K, value: V): Boolean = get(key)?.contains(value) ?: false

    /**
     * Gets a [Sequence] of key-value pairs. A given key will appear multiple times in the sequence if it is associated
     * with multiple values. This sequence lazily enumerates over keys and values in the multimap and may reflect
     * changes which occurred after the iteration began.
     */
    public val entryValues: Sequence<Map.Entry<K, V>>

    /**
     * Sets an association from the given [key] to the given [value]. This operation replaces any existing associations
     * between [key] and other values.
     * @param key The key to associate
     * @param value The value to associate
     * @return The previous values associated with [key] or null if no values were previously associated.
     */
    public fun put(key: K, value: V): MutableList<V>? = put(key, mutableListOf(value))

    /**
     * Removes the association from the given [key] to the given [value].
     * @param key The key to disassociate
     * @param value The value to disassociate
     * @return True if the given value was previously associated with the given key; otherwise, false.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeElement")
    public fun remove(key: K, value: V): Boolean

    /**
     * Removes the association from the given [key] to the value at the given [index] in the list of existing
     * associations.
     * @param key The key to disassociate
     * @param index The index of the value to disassociate
     * @return The value that was removed. If the given [key] wasn't associated with any values previously, returns
     * null.
     */
    public fun removeAt(key: K, index: Int): V?

    /**
     * Removes the associations from the given [key] to the given [values].
     * @param key The key to disassociate
     * @param values The values to disassociate
     * @return True if the list of associations was modified; otherwise, false.
     */
    public fun removeAll(key: K, values: Collection<V>): Boolean?

    /**
     * Retains only associations from the given [key] to the given [values]. Any other associations from the given [key]
     * to other values are removed.
     * @param key The key to disassociate
     * @param values The values to retrain
     * @param True if the list of associations was modified; otherwise, false.
     */
    public fun retainAll(key: K, values: Collection<V>): Boolean?

    /**
     * Returns a new read-only multimap containing all the key-value associations from this multimap
     */
    public fun toMultiMap(): MultiMap<K, V> = SimpleMultiMap(mapValues { (_, v) -> v.toList() }.toMap())
}

/**
 * Create a new [MutableMultiMap] from the given key-value pairs.
 * @param K The type of elements used as keys
 * @param V The type of elements used as values
 * @param pairs The elements to be contained by the new multimap
 */
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
