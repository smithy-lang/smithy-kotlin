/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.Deferred

/**
 * Mapping of String to a List of lazy values
 */
public interface DeferredValuesMap<T : Any> {

    /**
     * Flag indicating if this map compares keys ignoring case
     */
    public val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    public operator fun get(name: String): Deferred<T>? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    public fun getAll(name: String): List<Deferred<T>>?

    /**
     * Gets all names from the map
     */
    public fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    public fun entries(): Set<Map.Entry<String, List<Deferred<T>>>>

    /**
     * Checks if the given [name] exists in the map
     */
    public operator fun contains(name: String): Boolean

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    public fun contains(name: String, value: Deferred<T>): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    public fun forEach(body: (String, List<Deferred<T>>) -> Unit): Unit = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    public fun isEmpty(): Boolean
}

@InternalApi
internal open class DeferredValuesMapImpl<T : Any> (
    override val caseInsensitiveName: Boolean = false,
    initialValues: Map<String, List<Deferred<T>>> = emptyMap(),
) : DeferredValuesMap<T> {
    protected val values: Map<String, List<Deferred<T>>> = run {
        if (caseInsensitiveName) CaseInsensitiveMap<List<Deferred<T>>>().apply { putAll(initialValues) } else initialValues
    }

    override fun getAll(name: String): List<Deferred<T>>? = values[name]

    override fun names(): Set<String> = values.keys

    override fun entries(): Set<Map.Entry<String, List<Deferred<T>>>> = values.entries

    override operator fun contains(name: String): Boolean = values.containsKey(name)

    override fun contains(name: String, value: Deferred<T>): Boolean = getAll(name)?.contains(value) ?: false

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is DeferredValuesMap<*> &&
            caseInsensitiveName == other.caseInsensitiveName &&
            names().let { names ->
                if (names.size != other.names().size) {
                    return false
                }
                names.all { getAll(it) == other.getAll(it) }
            }
}

@InternalApi
public open class DeferredValuesMapBuilder<T : Any> (public val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<Deferred<T>>> =
        if (caseInsensitiveName) CaseInsensitiveMap() else LinkedHashMap(size)

    public fun getAll(name: String): List<Deferred<T>>? = values[name]

    public operator fun contains(name: String): Boolean = name in values

    public fun contains(name: String, value: Deferred<T>): Boolean = values[name]?.contains(value) ?: false

    public fun names(): Set<String> = values.keys

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun entries(): Set<Map.Entry<String, List<Deferred<T>>>> = values.entries

    public operator fun set(name: String, value: Deferred<T>) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    public fun setMissing(name: String, value: Deferred<T>) {
        if (!this.values.containsKey(name)) set(name, value)
    }

    public operator fun get(name: String): Deferred<T>? = getAll(name)?.firstOrNull()

    public fun append(name: String, value: Deferred<T>) {
        ensureListForKey(name, 1).add(value)
    }

    public fun appendAll(deferredValues: DeferredValuesMap<T>) {
        deferredValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    public fun appendMissing(deferredValues: DeferredValuesMap<T>) {
        deferredValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    public fun appendAll(name: String, values: Iterable<Deferred<T>>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                list.add(value)
            }
        }
    }

    public fun appendMissing(name: String, values: Iterable<Deferred<T>>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    public fun remove(name: String): MutableList<Deferred<T>>? = values.remove(name)

    public fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    public fun remove(name: String, value: Deferred<T>): Boolean = values[name]?.remove(value) ?: false

    public fun clear(): Unit = values.clear()

    public open fun build(): DeferredValuesMap<T> = DeferredValuesMapImpl(caseInsensitiveName, values)

    private fun ensureListForKey(name: String, size: Int): MutableList<Deferred<T>> =
        values[name] ?: ArrayList<Deferred<T>>(size).also { values[name] = it }
}
