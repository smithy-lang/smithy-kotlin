/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

/**
 * Specifies a key for an attribute
 *
 * @param T is the type of the vale stored in the attribute
 * @param name the name of the attribute (for diagnostics)
 */
public data class AttributeKey<T>(public val name: String) {
    override fun toString(): String = if (name.isBlank()) super.toString() else "ExecutionAttributeKey: $name"
}

/**
 * Immutable type safe property bag
 */
public interface Attributes {
    /**
     * Get a value of the attribute for the specified [key] or null
     */
    public fun <T : Any> getOrNull(key: AttributeKey<T>): T?

    /**
     * Check if an attribute with the specified [key] exists
     */
    public operator fun contains(key: AttributeKey<*>): Boolean

    /**
     * Get a set of all the keys
     */
    public val keys: Set<AttributeKey<*>>
}

/**
 * Mutable type safe property bag
 */
public interface MutableAttributes : Attributes {
    /**
     * Creates or changes an attribute with the specified [key] using [value]
     */
    public operator fun <T : Any> set(key: AttributeKey<T>, value: T)

    /**
     * Removes an attribute with the specified [key]
     */
    public fun <T : Any> remove(key: AttributeKey<T>)

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value
     */
    public fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T
}

/**
 * Gets a value of the attribute for the specified [key] or throws an [IllegalStateException] if key does not exist
 */
public operator fun <T : Any> Attributes.get(key: AttributeKey<T>): T = getOrNull(key) ?: throw IllegalStateException("No instance for $key")

/**
 * Removes an attribute with the specified [key] and returns its current value, throws an exception if an attribute doesn't exist
 */
public fun <T : Any> MutableAttributes.take(key: AttributeKey<T>): T = get(key).also { remove(key) }

/**
 * Set a value for [key] only if it is not already set
 */
public fun <T : Any> MutableAttributes.putIfAbsent(key: AttributeKey<T>, value: T) {
    if (!contains(key)) set(key, value)
}

/**
 * Set a value for [key] only if [value] is not null
 */
public fun <T : Any> MutableAttributes.setIfValueNotNull(key: AttributeKey<T>, value: T?) {
    if (value != null) set(key, value)
}

/**
 * Removes an attribute with the specified [key] and returns its current value, returns `null` if an attribute doesn't exist
 */
public fun <T : Any> MutableAttributes.takeOrNull(key: AttributeKey<T>): T? = getOrNull(key).also { remove(key) }

/**
 * Merge another attributes instance into this set of attributes favoring [other]
 */
public fun MutableAttributes.merge(other: Attributes) {
    other.keys.forEach {
        @Suppress("UNCHECKED_CAST")
        set(it as AttributeKey<Any>, other[it])
    }
}

private class AttributesImpl constructor(seed: Attributes) : MutableAttributes {
    private val map: MutableMap<AttributeKey<*>, Any> = mutableMapOf()
    constructor() : this(emptyAttributes())

    init {
        merge(seed)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    override fun contains(key: AttributeKey<*>): Boolean = map.contains(key)

    override fun <T : Any> set(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        val value = getOrNull(key)
        if (value != null) return value

        val result = block()
        map[key] = result
        return result
    }

    override val keys: Set<AttributeKey<*>>
        get() = map.keys
}

private object EmptyAttributes : Attributes {
    override val keys: Set<AttributeKey<*>> = emptySet()
    override fun contains(key: AttributeKey<*>): Boolean = false
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = null
}

/**
 * Returns an empty read-only set of attributes
 */
public fun emptyAttributes(): Attributes = EmptyAttributes

/**
 * Returns an empty new mutable set of attributes
 */
public fun mutableAttributes(): MutableAttributes = AttributesImpl()

public class AttributesBuilder {
    @PublishedApi
    internal val attributes: MutableAttributes = mutableAttributes()
    public infix fun <T : Any> AttributeKey<T>.to(value: T) {
        attributes[this] = value
    }

    public infix fun <T : Any> String.to(value: T) {
        attributes[AttributeKey<T>(this)] = value
    }
}

/**
 * Return a new set of mutable attributes using [AttributesBuilder].
 *
 * Example
 * ```kotlin
 * val attr1 = AttributeKey<String>("attribute 1")
 * val attr1 = AttributeKey<Int>("attribute 2")
 *
 * val attrs = mutableAttributesOf {
 *     attr1 to "value 1"
 *     attr2 to 57
 * }
 * ```
 */
public inline fun mutableAttributesOf(block: AttributesBuilder.() -> Unit): MutableAttributes =
    AttributesBuilder().apply(block).attributes

/**
 * Return a new set of attributes using [AttributesBuilder].
 *
 * Example
 * ```kotlin
 * val attr1 = AttributeKey<String>("attribute 1")
 * val attr1 = AttributeKey<Int>("attribute 2")
 *
 * val attrs = attributesOf {
 *     attr1 to "value 1"
 *     attr2 to 57
 * }
 * ```
 */
public inline fun attributesOf(block: AttributesBuilder.() -> Unit): Attributes =
    mutableAttributesOf(block)

/**
 * Returns a new [MutableAttributes] instance with elements from this set of attributes.
 */
public fun Attributes.toMutableAttributes(): MutableAttributes = AttributesImpl(this)
