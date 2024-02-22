/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

/**
 * Creates a new list or appends to an existing one if not null.
 *
 * If [dest] is null this function creates a new list with element [x] and returns it.
 * Otherwise, it appends [x] to [dest] and returns the mutated list.
 */
public fun <T> createOrAppend(dest: List<T>?, x: T): List<T> {
    if (dest == null) return listOf(x)
    val mut = dest.toMutableList()
    mut.add(x)
    return mut
}
