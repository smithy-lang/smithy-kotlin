/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public fun Short.toNumber(): Short = this

@InternalApi
public fun Int.toNumber(): Int = this

@InternalApi
public fun Long.toNumber(): Long = this

@InternalApi
public fun Float.toNumber(): Float = this

@InternalApi
public fun Double.toNumber(): Double = this

@InternalApi
public fun String.toNumber(): Double? = this.toDoubleOrNull()

@InternalApi
public fun Any.toNumber(): Nothing? = null

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

@InternalApi
@JvmName("noOpUnnestedCollection")
public inline fun <reified T> Collection<T>.flattenIfPossible(): Collection<T> = this

@InternalApi
@JvmName("flattenNestedCollection")
public inline fun <reified T> Collection<Collection<T>>.flattenIfPossible(): Collection<T> = flatten()

/**
 * Determines the length of a collection. This is a synonym for [Collection.size].
 */
@InternalApi
public val <T> Collection<T>.length: Int
    get() = size

/**
 * Returns a JS type as a string
 *
 * See: [JMESPath spec](https://jmespath.org/specification.html#type)
 */
@InternalApi
public fun Any?.type(): String = when (this) {
    is String -> "string"
    is Boolean -> "boolean"
    is List<*>, is Array<*> -> "array"
    is Number -> "number"
    is Any -> "object"
    null -> "null"
    else -> throw Exception("Undetected type for: $this")
}
