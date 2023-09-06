/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
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

public data class Property (val name: String, val value: Any?)

@InternalApi
@Suppress("UNCHECKED_CAST")
public fun Any.getProperties(): List<Property> =
    this::class.declaredMemberProperties.map {
        Property(name = it.name, value = (it as KProperty1<Any, *>).get(this))
    }

/**
 * Merges zero or more objects' properties together (objects with same property name override each-other)
 */
public fun List<Any>.mergeObjects(): HashMap<String, Any?> {
    val newObject = HashMap<String, Any?>()

    forEach { obj ->
        obj.getProperties().forEach { property ->
            newObject[property.name] = property.value
        }
    }

    return newObject
}

public val map: HashMap<String, Any?> = hashMapOf("num" to 1, "string" to "foo", "null" to null)

public val x: Property = Property::class.createInstance().apply {
    map.forEach { (name, value) ->

    }
}

public val Property.prop: String
    get() = ""

// Create a class reference using reflection
val clazz = DynamicObject::class

// Create an instance of the class using reflection and set its properties
val dynamicObject = clazz.createInstance().apply {
    properties.forEach { (propertyName, propertyValue) ->
        val property = clazz.declaredMemberProperties
            .firstOrNull { it.name == propertyName }
        property?.let {
            it.isAccessible = true
            it.setter.call(this, propertyValue)
        }
    }
}