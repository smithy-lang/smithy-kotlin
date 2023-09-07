/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

/**
 * Determines the length of a collection. This is a synonym for [Collection.size].
 */
@InternalApi
public val <T> Collection<T>.length: Int
    get() = size

@InternalApi
@JvmName("noOpUnnestedCollection")
public inline fun <reified T> Collection<T>.flattenIfPossible(): Collection<T> = this

@InternalApi
@JvmName("flattenNestedCollection")
public inline fun <reified T> Collection<Collection<T>>.flattenIfPossible(): Collection<T> = flatten()

/**
 * Evaluates the "truthiness" of a value based on
 * [JMESPath definitions](https://jmespath.org/specification.html#or-expressions).
 */
@InternalApi
public fun truthiness(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Collection<*> -> value.isNotEmpty()
    is Map<*, *> -> value.isNotEmpty()
    is String -> value.isNotEmpty()
    null -> false
    else -> true
}

public data class Property(val name: String, val value: Any?)

@InternalApi
@Suppress("UNCHECKED_CAST")
public fun Any.getProperties(): List<Property> =
    this::class.declaredMemberProperties.map {
        Property(name = it.name, value = (it as KProperty1<Any, *>).get(this))
    }

@InternalApi
public fun HashMap<String, Any?>.getProperties(): List<Property> =
    this.map {
        Property(it.key, it.value)
    }

/**
 * Merges zero or more objects' properties together (objects with same property name override each-other)
 */
public fun List<Any?>.mergeObjects(): HashMap<String, Any?> {
    val newObject = HashMap<String, Any?>()

    forEach { obj ->
        obj?.let {
            obj.getProperties().forEach { property ->
                newObject[property.name] = property.value
            }
        }
    }

    return newObject
}
