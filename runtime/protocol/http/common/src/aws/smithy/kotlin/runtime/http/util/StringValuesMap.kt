/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Mapping of String to a List of Strings values
 */
public interface StringValuesMap {

    /**
     * Flag indicating if this map compares keys ignoring case
     */
    public val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    public operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    public fun getAll(name: String): List<String>?

    /**
     * Gets all names from the map
     */
    public fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    public fun entries(): Set<Map.Entry<String, List<String>>>

    /**
     * Checks if the given [name] exists in the map
     */
    public operator fun contains(name: String): Boolean

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    public fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    public fun forEach(body: (String, List<String>) -> Unit): Unit = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    public fun isEmpty(): Boolean
}

@InternalApi
internal open class StringValuesMapImpl(
    override val caseInsensitiveName: Boolean = false,
    initialValues: Map<String, List<String>> = emptyMap(),
) : StringValuesMap {
    protected val values: Map<String, List<String>> = run {
        // Make a defensive copy so modifications to the initialValues don't mutate our internal copy
        val copiedValues = initialValues.deepCopy()
        if (caseInsensitiveName) CaseInsensitiveMap<List<String>>().apply { putAll(copiedValues) } else copiedValues
    }

    override fun getAll(name: String): List<String>? = values[name]

    override fun names(): Set<String> = values.keys

    override fun entries(): Set<Map.Entry<String, List<String>>> = values.entries

    override operator fun contains(name: String): Boolean = values.containsKey(name)

    override fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

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
}

/**
 * Perform a deep copy of this map, specifically duplicating the value lists so that they're insulated from changes.
 * @return A new map instance with copied value lists.
 */
internal fun Map<String, List<String>>.deepCopy() = mapValues { (_, v) -> v.toMutableList() }

@InternalApi
public open class StringValuesMapBuilder(public val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<String>> =
        if (caseInsensitiveName) CaseInsensitiveMap() else LinkedHashMap(size)

    public fun getAll(name: String): List<String>? = values[name]

    public operator fun contains(name: String): Boolean = name in values

    public fun contains(name: String, value: String): Boolean = values[name]?.contains(value) ?: false

    public fun names(): Set<String> = values.keys

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun entries(): Set<Map.Entry<String, List<String>>> = values.entries

    public operator fun set(name: String, value: String) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    public fun setMissing(name: String, value: String) {
        if (!this.values.containsKey(name)) set(name, value)
    }

    public operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    public fun append(name: String, value: String) {
        ensureListForKey(name, 1).add(value)
    }

    public fun appendAll(stringValues: StringValuesMap) {
        stringValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    public fun appendMissing(stringValues: StringValuesMap) {
        stringValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    public fun appendAll(name: String, values: Iterable<String>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                list.add(value)
            }
        }
    }

    public fun appendMissing(name: String, values: Iterable<String>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    public fun remove(name: String): MutableList<String>? = values.remove(name)

    public fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    public fun remove(name: String, value: String): Boolean = values[name]?.remove(value) ?: false

    public fun clear(): Unit = values.clear()

    public open fun build(): StringValuesMap = StringValuesMapImpl(caseInsensitiveName, values)

    private fun ensureListForKey(name: String, size: Int): MutableList<String> =
        values[name] ?: ArrayList<String>(size).also { values[name] = it }
}
