/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Mapping of [String] to a List of [T] values
 */
public interface ValuesMap<T> {

    /**
     * Flag indicating if this map compares keys ignoring case
     */
    public val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    public operator fun get(name: String): T? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    public fun getAll(name: String): List<T>?

    /**
     * Gets all names from the map
     */
    public fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    public fun entries(): Set<Map.Entry<String, List<T>>>

    /**
     * Checks if the given [name] exists in the map
     */
    public operator fun contains(name: String): Boolean

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    public fun contains(name: String, value: T): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    public fun forEach(body: (String, List<T>) -> Unit): Unit = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    public fun isEmpty(): Boolean
}

/**
 * Perform a deep copy of this map, specifically duplicating the value lists so that they're insulated from changes.
 * @return A new map instance with copied value lists.
 */
@InternalApi
public fun <T> Map<String, MutableList<T>>.deepCopy(): Map<String, MutableList<T>> = mapValues { (_, v) -> v.toMutableList() }

@InternalApi
public open class ValuesMapImpl<T>(
    override val caseInsensitiveName: Boolean = false,
    initialValues: Map<String, List<T>> = emptyMap(),
) : ValuesMap<T> {
    protected val values: Map<String, List<T>> = run {
        // Make a defensive copy so modifications to the initialValues don't mutate our internal copy
        val copiedValues = initialValues.deepCopyValues()
        if (caseInsensitiveName) CaseInsensitiveMap<List<T>>().apply { putAll(copiedValues) } else copiedValues
    }

    override fun getAll(name: String): List<T>? = values[name]

    override fun names(): Set<String> = values.keys

    override fun entries(): Set<Map.Entry<String, List<T>>> = values.entries

    override operator fun contains(name: String): Boolean = values.containsKey(name)

    override fun contains(name: String, value: T): Boolean = getAll(name)?.contains(value) ?: false

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is ValuesMap<*> &&
            caseInsensitiveName == other.caseInsensitiveName &&
            names().let { names ->
                if (names.size != other.names().size) {
                    return false
                }
                names.all { getAll(it) == other.getAll(it) }
            }

    private fun Map<String, List<T>>.deepCopyValues(): Map<String, List<T>> = mapValues { (_, v) -> v.toList() }
}

@InternalApi
public open class ValuesMapBuilder<T>(public val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<T>> =
        if (caseInsensitiveName) CaseInsensitiveMap() else LinkedHashMap(size)

    public fun getAll(name: String): List<T>? = values[name]

    public operator fun contains(name: String): Boolean = name in values

    public fun contains(name: String, value: T): Boolean = values[name]?.contains(value) ?: false

    public fun names(): Set<String> = values.keys

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun entries(): Set<Map.Entry<String, List<T>>> = values.entries

    public operator fun set(name: String, value: T) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    public fun setMissing(name: String, value: T) {
        if (!this.values.containsKey(name)) set(name, value)
    }

    public operator fun get(name: String): T? = getAll(name)?.firstOrNull()

    public fun append(name: String, value: T) {
        ensureListForKey(name, 1).add(value)
    }

    public fun appendAll(valuesMap: ValuesMap<T>) {
        valuesMap.forEach { name, values ->
            appendAll(name, values)
        }
    }

    public fun appendMissing(valuesMap: ValuesMap<T>) {
        valuesMap.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    public fun appendAll(name: String, values: Iterable<T>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                list.add(value)
            }
        }
    }

    public fun appendMissing(name: String, values: Iterable<T>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    public fun remove(name: String): MutableList<T>? = values.remove(name)

    public fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    public fun remove(name: String, value: T): Boolean = values[name]?.remove(value) ?: false

    public fun clear(): Unit = values.clear()

    public open fun build(): ValuesMap<T> = ValuesMapImpl(caseInsensitiveName, values)

    private fun ensureListForKey(name: String, size: Int): MutableList<T> =
        values[name] ?: ArrayList<T>(size).also { values[name] = it }
}
