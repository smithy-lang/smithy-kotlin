/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi

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

@InternalApi
public fun List<Number>.max(): Double? =
    @Suppress("UNCHECKED_CAST")
    (this as List<Double>).maxOrNull()

@InternalApi
public fun List<String>.max(): String? = this.maxOrNull()

@InternalApi
public fun List<Number>.min(): Double? =
    @Suppress("UNCHECKED_CAST")
    (this as List<Double>).minOrNull()

@InternalApi
public fun List<String>.min(): String? = this.minOrNull()

@InternalApi
public fun Number.toNumber(): Double = this.toDouble()

@InternalApi
public fun String.toNumber(): Double? = this.toDoubleOrNull()
