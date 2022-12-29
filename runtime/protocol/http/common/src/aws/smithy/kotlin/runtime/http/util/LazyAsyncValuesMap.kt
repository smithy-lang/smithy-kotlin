/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.LazyAsyncValue

/**
 * Mapping of String to a List of lazy values
 */
public interface LazyAsyncValuesMap<T : Any> {

    /**
     * Flag indicating if this map compares keys ignoring case
     */
    public val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    public operator fun get(name: String): LazyAsyncValue<T>? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    public fun getAll(name: String): List<LazyAsyncValue<T>>?

    /**
     * Gets all names from the map
     */
    public fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    public fun entries(): Set<Map.Entry<String, List<LazyAsyncValue<T>>>>

    /**
     * Checks if the given [name] exists in the map
     */
    public operator fun contains(name: String): Boolean

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    public fun contains(name: String, value: LazyAsyncValue<T>): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    public fun forEach(body: (String, List<LazyAsyncValue<T>>) -> Unit): Unit = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    public fun isEmpty(): Boolean
}

@InternalApi
internal open class LazyAsyncValuesMapImpl<T : Any> (
    override val caseInsensitiveName: Boolean = false,
    initialValues: Map<String, List<LazyAsyncValue<T>>> = emptyMap(),
) : LazyAsyncValuesMap<T> {
    protected val values: Map<String, List<LazyAsyncValue<T>>> = run {
        // Make a defensive copy so modifications to the initialValues don't mutate our internal copy
        val copiedValues = initialValues.deepCopy()
        if (caseInsensitiveName) CaseInsensitiveMap<List<LazyAsyncValue<T>>>().apply { putAll(copiedValues) } else copiedValues
    }

    override fun getAll(name: String): List<LazyAsyncValue<T>>? = values[name]

    override fun names(): Set<String> = values.keys

    override fun entries(): Set<Map.Entry<String, List<LazyAsyncValue<T>>>> = values.entries

    override operator fun contains(name: String): Boolean = values.containsKey(name)

    override fun contains(name: String, value: LazyAsyncValue<T>): Boolean = getAll(name)?.contains(value) ?: false

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is StringValuesMap &&
            caseInsensitiveName == other.caseInsensitiveName &&
            names().let { names ->
                if (names.size != other.names().size) {
                    return false
                }
                names.all { getAll(it) == other.getAll(it) }
            }

    /**
     * Perform a deep copy of this map, specifically duplicating the value lists so that they're insulated from changes.
     * @return A new map instance with copied value lists.
     */
    private fun Map<String, List<LazyAsyncValue<T>>>.deepCopy() = mapValues { (_, v) -> v.toMutableList() }
}

@InternalApi
public open class LazyAsyncValuesMapBuilder<T : Any> (public val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<LazyAsyncValue<T>>> =
        if (caseInsensitiveName) CaseInsensitiveMap() else LinkedHashMap(size)

    public fun getAll(name: String): List<LazyAsyncValue<T>>? = values[name]

    public operator fun contains(name: String): Boolean = name in values

    public fun contains(name: String, value: LazyAsyncValue<T>): Boolean = values[name]?.contains(value) ?: false

    public fun names(): Set<String> = values.keys

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun entries(): Set<Map.Entry<String, List<LazyAsyncValue<T>>>> = values.entries

    public operator fun set(name: String, value: LazyAsyncValue<T>) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    public fun setMissing(name: String, value: LazyAsyncValue<T>) {
        if (!this.values.containsKey(name)) set(name, value)
    }

    public operator fun get(name: String): LazyAsyncValue<T>? = getAll(name)?.firstOrNull()

    public fun append(name: String, value: LazyAsyncValue<T>) {
        ensureListForKey(name, 1).add(value)
    }

    public fun appendAll(lazyAsyncValues: LazyAsyncValuesMap<T>) {
        lazyAsyncValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    public fun appendMissing(lazyAsyncValues: LazyAsyncValuesMap<T>) {
        lazyAsyncValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    public fun appendAll(name: String, values: Iterable<LazyAsyncValue<T>>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                list.add(value)
            }
        }
    }

    public fun appendMissing(name: String, values: Iterable<LazyAsyncValue<T>>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    public fun remove(name: String): MutableList<LazyAsyncValue<T>>? = values.remove(name)

    public fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    public fun remove(name: String, value: LazyAsyncValue<T>): Boolean = values[name]?.remove(value) ?: false

    public fun clear(): Unit = values.clear()

    public open fun build(): LazyAsyncValuesMap<T> = LazyAsyncValuesMapImpl(caseInsensitiveName, values)

    private fun ensureListForKey(name: String, size: Int): MutableList<LazyAsyncValue<T>> =
        values[name] ?: ArrayList<LazyAsyncValue<T>>(size).also { values[name] = it }
}
