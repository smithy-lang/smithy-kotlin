/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

import kotlin.jvm.JvmName

/**
 * Determines the length of a collection. This is a synonym for [Collection.size].
 */
val <T> Collection<T>.length: Int
    get() = size

@JvmName("noOpUnnestedCollection")
inline fun <reified T> Collection<T>.flattenIfPossible(): Collection<T> = this

@JvmName("flattenNestedCollection")
inline fun <reified T> Collection<Collection<T>>.flattenIfPossible(): Collection<T> = flatten()
