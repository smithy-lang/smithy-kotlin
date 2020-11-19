/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.util

/**
 * Specifies a key for an attribute
 *
 * @param T is the type of the vale stored in the attribute
 * @param name the name of the attribute (for diagnostics)
 */
class AttributeKey<T>(val name: String) {
    override fun toString(): String = if (name.isBlank()) super.toString() else "ExecutionAttributeKey: $name"
}

/**
 * Generic type safe property bag
 */
interface Attributes {
    /**
     * Get a value of the attribute for the specified [key] or null
     */
    fun <T : Any> getOrNull(key: AttributeKey<T>): T?

    /**
     * Check if an attribute with the specified [key] exists
     */
    operator fun contains(key: AttributeKey<*>): Boolean

    /**
     * Creates or changes an attribute with the specified [key] using [value]
     */
    operator fun <T : Any> set(key: AttributeKey<T>, value: T)

    /**
     * Removes an attribute with the specified [key]
     */
    fun <T : Any> remove(key: AttributeKey<T>)

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value
     */
    fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T

    companion object {
        /**
         * Create an attributes instance
         */
        operator fun invoke(): Attributes = AttributesImpl()
    }
}

/**
 * Gets a value of the attribute for the specified [key] or throws an [IllegalStateException] if key does not exist
 */
operator fun <T : Any> Attributes.get(key: AttributeKey<T>): T = getOrNull(key) ?: throw IllegalStateException("No instance for $key")

/**
 * Removes an attribute with the specified [key] and returns its current value, throws an exception if an attribute doesn't exist
 */
fun <T : Any> Attributes.take(key: AttributeKey<T>): T = get(key).also { remove(key) }

/**
 * Removes an attribute with the specified [key] and returns its current value, returns `null` if an attribute doesn't exist
 */
fun <T : Any> Attributes.takeOrNull(key: AttributeKey<T>): T? = getOrNull(key).also { remove(key) }

private class AttributesImpl : Attributes {
    private val map: MutableMap<AttributeKey<*>, Any> = mutableMapOf()

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
}
