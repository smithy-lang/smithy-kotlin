/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import software.aws.clientrt.util.InternalAPI

/**
 * Mapping of String to a List of Strings values
 */
interface StringValuesMap {

    /**
     * Flag indicating if this map compares keys ignoring case
     */
    val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    fun getAll(name: String): List<String>?

    /**
     * Gets all names from the map
     */
    fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    fun entries(): Set<Map.Entry<String, List<String>>>

    /**
     * Checks if the given [name] exists in the map
     */
    operator fun contains(name: String): Boolean

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    fun forEach(body: (String, List<String>) -> Unit) = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    fun isEmpty(): Boolean
}

@InternalAPI
internal open class StringValuesMapImpl(
    override val caseInsensitiveName: Boolean = false,
    initialValues: Map<String, List<String>> = emptyMap()
) : StringValuesMap {
    protected val values: Map<String, List<String>> by lazy {
        if (caseInsensitiveName) CaseInsensitiveMap<List<String>>().apply { putAll(initialValues) } else values.toMap()
    }

    override fun getAll(name: String): List<String>? = values[name]

    override fun names(): Set<String> = values.keys

    override fun entries(): Set<Map.Entry<String, List<String>>> = values.entries

    override operator fun contains(name: String): Boolean = values.containsKey(name)

    override fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

    override fun isEmpty(): Boolean = values.isEmpty()
}

@InternalAPI
open class StringValuesMapBuilder(val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<String>> =
        if (caseInsensitiveName) CaseInsensitiveMap() else LinkedHashMap(size)

    protected var built = false

    fun getAll(name: String): List<String>? = values[name]

    operator fun contains(name: String): Boolean = name in values

    fun contains(name: String, value: String): Boolean = values[name]?.contains(value) ?: false

    fun names(): Set<String> = values.keys

    fun isEmpty(): Boolean = values.isEmpty()

    fun entries(): Set<Map.Entry<String, List<String>>> = values.entries

    operator fun set(name: String, value: String) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    fun append(name: String, value: String) {
        ensureListForKey(name, 1).add(value)
    }

    fun appendAll(stringValues: StringValuesMap) {
        stringValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    fun appendMissing(stringValues: StringValuesMap) {
        stringValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    fun appendAll(name: String, values: Iterable<String>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                list.add(value)
            }
        }
    }

    fun appendMissing(name: String, values: Iterable<String>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    fun remove(name: String) = values.remove(name)

    fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    fun remove(name: String, value: String) = values[name]?.remove(value) ?: false

    fun clear() = values.clear()

    open fun build(): StringValuesMap {
        require(!built) { "StringValuesMapBuilder can only build a single instance" }
        built = true
        return StringValuesMapImpl(caseInsensitiveName, values)
    }

    private fun ensureListForKey(name: String, size: Int): MutableList<String> {
        if (built)
            throw IllegalStateException("Cannot modify a builder when final structure has already been built")
        return values[name] ?: ArrayList<String>(size).also { values[name] = it }
    }
}
